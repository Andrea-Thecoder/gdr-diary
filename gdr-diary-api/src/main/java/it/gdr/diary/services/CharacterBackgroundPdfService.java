package it.gdr.diary.services;

import io.ebean.Database;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.model.CharacterBackground;
import it.gdr.diary.model.GameCharacter;
import it.gdr.diary.model.GameCharacterKind;
import it.gdr.diary.security.JwtInspector;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CharacterBackgroundPdfService {

    static final String TITLE_PREFIX = "Il Background di ";
    static final String FILE_PREFIX = "background-";
    static final String FILE_FALLBACK = "personaggio";
    private static final int FILE_NAME_MAX_LENGTH = 60;

    private final Database database;
    private final JwtInspector jwtInspector;
    private final BackgroundPdfRenderer renderer;

    public BackgroundPdf getCharacterBackgroundPdf(UUID characterId) {
        GameCharacter character = getGameCharacterByIdAndOwner(characterId);
        CharacterBackground background = database.find(CharacterBackground.class)
                .setLabel("getCharacterBackgroundPdf")
                .where().eq("character.id", characterId).eq("character.owner.keycloakId", jwtInspector.getSubject())
                .findOne();
        if (background == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Background not found");
        }
        byte[] content = renderer.render(TITLE_PREFIX + character.getName(),
                StringUtils.trimToNull(character.getCampaignName()), background.getContent());
        return new BackgroundPdf(fileName(character.getName()), content);
    }

    static String fileName(String characterName) {
        String plain = Normalizer.normalize(StringUtils.defaultString(characterName), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        String shortened = StringUtils.left(plain, FILE_NAME_MAX_LENGTH).replaceAll("-+$", "");
        return FILE_PREFIX + (shortened.isEmpty() ? FILE_FALLBACK : shortened) + ".pdf";
    }

    private GameCharacter getGameCharacterByIdAndOwner(UUID id) {
        GameCharacter character = database.find(GameCharacter.class)
                .setLabel("getGameCharacterByIdAndOwner")
                .where().idEq(id).eq("owner.keycloakId", jwtInspector.getSubject())
                .findOne();
        if (character == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Character not found");
        }
        if (character.getKind() == GameCharacterKind.GAME_MASTER) {
            throw new ServiceException("A game master has no background");
        }
        return character;
    }
}
