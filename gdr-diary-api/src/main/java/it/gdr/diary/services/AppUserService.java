package it.gdr.diary.services;

import io.ebean.Database;
import io.ebean.DuplicateKeyException;
import io.ebean.Transaction;
import it.gdr.diary.config.AppUserConfig;
import it.gdr.diary.dto.app_user.DetailAppUserDTO;
import it.gdr.diary.dto.app_user.UpdateSettingsAppUserDTO;
import it.gdr.diary.validation.StorableText;
import it.gdr.diary.exception.RecentAuthenticationRequiredException;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.exception.SqlStates;
import it.gdr.diary.model.AppUser;
import it.gdr.diary.model.Diary;
import it.gdr.diary.model.DiaryPage;
import it.gdr.diary.model.Document;
import it.gdr.diary.model.GameCharacter;
import it.gdr.diary.security.JwtInspector;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AppUserService {

    private final Database database;
    private final JwtInspector jwtInspector;
    private final ObjectMapper objectMapper;
    private final AppUserConfig config;
    private final DocumentService documentService;
    private final Clock clock;

    public UUID createAppUser() {
        String keycloakId = jwtInspector.getSubject();
        String email = jwtInspector.getEmail();
        if (StringUtils.isBlank(email)) {
            throw new ServiceException("The token has no email address");
        }
        AppUser user = findAppUserByKeycloakId(keycloakId)
                .map(existing -> updateAppUserEmail(existing, email))
                .orElseGet(() -> insertAppUser(keycloakId, email));
        return user.getId();
    }

    public DetailAppUserDTO getAppUserBySubject() {
        AppUser user = findAppUserOrThrow();
        return DetailAppUserDTO.from(user, documentService.getDocumentUrl(user.getAvatarDocument()));
    }

    public String updateAppUserAvatar(MultipartFile file) {
        AppUser user = findAppUserOrThrow();
        Document previous = user.getAvatarDocument();
        Document avatar = null;
        try (Transaction transaction = database.beginTransaction()) {
            avatar = documentService.createImageDocument(file);
            user.setAvatarDocument(avatar);
            database.save(user);
            documentService.deleteDocumentRecord(previous);
            transaction.commit();
        } catch (RuntimeException e) {
            documentService.deleteDocumentFile(avatar);
            throw e;
        }
        documentService.deleteDocumentFile(previous);
        return documentService.getDocumentUrl(avatar);
    }

    public void deleteAppUserAvatar() {
        AppUser user = findAppUserOrThrow();
        Document previous = user.getAvatarDocument();
        try (Transaction transaction = database.beginTransaction()) {
            user.setAvatarDocument(null);
            database.save(user);
            documentService.deleteDocumentRecord(previous);
            transaction.commit();
        }
        documentService.deleteDocumentFile(previous);
    }

    public void deleteAppUser() {
        requireRecentAuthentication();
        List<Document> documents;
        try {
            documents = deleteAppUserData(jwtInspector.getSubject());
        } catch (PersistenceException e) {
            if (SqlStates.isForeignKeyViolation(e)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "The account changed while it was being deleted, try again");
            }
            throw e;
        }
        documents.forEach(documentService::deleteDocumentFile);
    }

    private void requireRecentAuthentication() {
        Duration maxAge = config.getDeleteMaxAuthAge();
        Instant limit = clock.instant().minus(maxAge);
        boolean recent = jwtInspector.getAuthTime().filter(authTime -> !authTime.isBefore(limit)).isPresent();
        if (!recent) {
            throw new RecentAuthenticationRequiredException(maxAge.toSeconds());
        }
    }

    private List<Document> deleteAppUserData(String keycloakId) {
        Optional<AppUser> found = findAppUserByKeycloakId(keycloakId);
        if (found.isEmpty()) {
            return List.of();
        }
        AppUser user = found.get();
        List<Document> documents = new ArrayList<>();
        if (user.getAvatarDocument() != null) {
            documents.add(user.getAvatarDocument());
        }
        try (Transaction transaction = database.beginTransaction()) {
            List<GameCharacter> characters = database.find(GameCharacter.class)
                    .setLabel("deleteAppUserCharacters")
                    .fetch("avatarDocument")
                    .where().eq("owner.id", user.getId())
                    .findList();
            if (!characters.isEmpty()) {
                List<Diary> diaries = database.find(Diary.class)
                        .setLabel("deleteAppUserDiaries")
                        .fetch("coverDocument")
                        .where().in("character.id", characters.stream().map(GameCharacter::getId).toList())
                        .findList();
                if (!diaries.isEmpty()) {
                    database.find(DiaryPage.class)
                            .setLabel("deleteAppUserPages")
                            .where().in("diary.id", diaries.stream().map(Diary::getId).toList())
                            .delete();
                    diaries.stream().map(Diary::getCoverDocument).filter(Objects::nonNull).forEach(documents::add);
                    database.deleteAll(diaries);
                }
                characters.stream().map(GameCharacter::getAvatarDocument).filter(Objects::nonNull).forEach(documents::add);
                database.deleteAll(characters);
            }
            database.delete(user);
            documents.forEach(documentService::deleteDocumentRecord);
            transaction.commit();
        }
        return documents;
    }

    public Map<String, Object> updateAppUserSettings(UpdateSettingsAppUserDTO settings) {
        AppUser user = findAppUserOrThrow();
        Map<String, Object> merged = user.getSettings() == null ? new HashMap<>() : new HashMap<>(user.getSettings());
        settings.settings().forEach((key, value) -> {
            if (value == null) {
                merged.remove(key);
            } else {
                merged.put(key, value);
            }
        });
        validateSettings(merged);
        user.setSettings(merged.isEmpty() ? null : merged);
        database.save(user);
        return merged;
    }

    private AppUser findAppUserOrThrow() {
        return findAppUserByKeycloakId(jwtInspector.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void validateSettings(Map<String, Object> settings) {
        if (containsUnstorableText(settings)) {
            throw new ServiceException("Settings contain characters that cannot be stored");
        }
        if (objectMapper.writeValueAsBytes(settings).length > config.getMaxSettingsSize().toBytes()) {
            throw new ServiceException("Settings too large");
        }
    }

    private static boolean containsUnstorableText(Object value) {
        return switch (value) {
            case String text -> !StorableText.isStorable(text);
            case Map<?, ?> map -> map.entrySet().stream()
                    .anyMatch(entry -> containsUnstorableText(entry.getKey()) || containsUnstorableText(entry.getValue()));
            case Collection<?> items -> items.stream().anyMatch(AppUserService::containsUnstorableText);
            case null, default -> false;
        };
    }

    private AppUser insertAppUser(String keycloakId, String email) {
        AppUser user = new AppUser();
        user.setKeycloakId(keycloakId);
        user.setEmail(email);
        try {
            database.save(user);
            return user;
        } catch (DuplicateKeyException e) {
            return findAppUserByKeycloakId(keycloakId)
                    .orElseThrow(() -> new ServiceException("User not found"));
        }
    }

    private AppUser updateAppUserEmail(AppUser user, String email) {
        if (!Objects.equals(user.getEmail(), email)) {
            user.setEmail(email);
            database.save(user);
        }
        return user;
    }

    private Optional<AppUser> findAppUserByKeycloakId(String keycloakId) {
        return database.find(AppUser.class).setLabel("findAppUserByKeycloakId").fetch("avatarDocument").where().eq("keycloakId", keycloakId).findOneOrEmpty();
    }
}
