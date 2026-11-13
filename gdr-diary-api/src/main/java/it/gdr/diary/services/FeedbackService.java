package it.gdr.diary.services;

import io.ebean.Database;
import io.ebean.ExpressionList;
import io.ebean.Transaction;
import it.gdr.diary.config.FeedbackConfig;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.feedback.AttachmentFeedbackDTO;
import it.gdr.diary.dto.feedback.BaseFeedbackDTO;
import it.gdr.diary.dto.feedback.DetailFeedbackDTO;
import it.gdr.diary.dto.feedback.InsertFeedbackDTO;
import it.gdr.diary.dto.search.FeedbackSearchRequest;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.model.Document;
import it.gdr.diary.model.Feedback;
import it.gdr.diary.model.FeedbackDocument;
import it.gdr.diary.model.FeedbackStatus;
import it.gdr.diary.model.FeedbackType;
import it.gdr.diary.validation.StorableText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final Database database;
    private final DocumentService documentService;
    private final FeedbackConfig config;
    private final ObjectMapper objectMapper;

    public PagedResultDTO<BaseFeedbackDTO> findAllFeedbacks(FeedbackSearchRequest request) {
        ExpressionList<Feedback> query = database.find(Feedback.class)
                .setLabel("findAllFeedbacks")
                .where();
        request.applyFilter(query, config.getTimezone());
        request.applySortAndPagination(query, "createdAt");
        return PagedResultDTO.of(query.findPagedList(), BaseFeedbackDTO::from);
    }

    public DetailFeedbackDTO getFeedbackById(UUID id) {
        return toDetailFeedbackDTO(getFeedbackByIdOrThrow(id));
    }

    public void createFeedback(InsertFeedbackDTO request, List<MultipartFile> files) {
        if (StringUtils.isNotBlank(request.website())) {
            log.warn("Feedback discarded: the trap field was filled");
            return;
        }
        List<MultipartFile> uploads = files == null ? List.of() : files.stream().filter(file -> !file.isEmpty()).toList();
        validateUploads(uploads);
        Feedback feedback = new Feedback();
        feedback.setType(FeedbackType.valueOf(request.type().trim().toUpperCase(Locale.ROOT)));
        feedback.setStatus(FeedbackStatus.NEW);
        feedback.setEmail(StringUtils.trim(request.email()));
        feedback.setPrivacyAccepted(true);
        feedback.setMessage(StringUtils.trim(request.message()));
        feedback.setTechnicalInfo(parseTechnicalInfo(request.technicalInfo()));
        List<Document> documents = new ArrayList<>();
        try (Transaction transaction = database.beginTransaction()) {
            database.save(feedback);
            for (MultipartFile file : uploads) {
                Document document = documentService.createImageDocument(file, config.getBucket());
                documents.add(document);
                FeedbackDocument link = new FeedbackDocument();
                link.setFeedback(feedback);
                link.setDocument(document);
                database.save(link);
            }
            transaction.commit();
        } catch (RuntimeException e) {
            documents.forEach(documentService::deleteDocumentFile);
            throw e;
        }
    }

    public DetailFeedbackDTO markFeedbackAsRead(UUID id) {
        return updateFeedbackStatus(id, FeedbackStatus.READ);
    }

    public DetailFeedbackDTO markFeedbackAsUnread(UUID id) {
        return updateFeedbackStatus(id, FeedbackStatus.NEW);
    }

    public DetailFeedbackDTO markFeedbackAsAnswered(UUID id) {
        return updateFeedbackStatus(id, FeedbackStatus.ANSWERED);
    }

    public DetailFeedbackDTO archiveFeedback(UUID id) {
        return updateFeedbackStatus(id, FeedbackStatus.ARCHIVED);
    }

    public void deleteFeedback(UUID id) {
        Feedback feedback = getFeedbackByIdOrThrow(id);
        List<FeedbackDocument> links = findFeedbackAttachments(feedback.getId());
        List<Document> documents = links.stream().map(FeedbackDocument::getDocument).toList();
        try (Transaction transaction = database.beginTransaction()) {
            database.deleteAll(links);
            database.delete(feedback);
            documents.forEach(documentService::deleteDocumentRecord);
            transaction.commit();
        }
        documents.forEach(documentService::deleteDocumentFile);
    }

    private DetailFeedbackDTO updateFeedbackStatus(UUID id, FeedbackStatus status) {
        Feedback feedback = getFeedbackByIdOrThrow(id);
        feedback.setStatus(status);
        database.save(feedback);
        return toDetailFeedbackDTO(feedback);
    }

    private void validateUploads(List<MultipartFile> uploads) {
        if (uploads.size() > config.getMaxFiles()) {
            throw new ServiceException("Too many files (maximum " + config.getMaxFiles() + ")");
        }
        long totalSize = uploads.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > config.getMaxTotalFileSize().toBytes()) {
            throw new ServiceException("Files too large (maximum total " + config.getMaxTotalFileSize().toMegabytes() + " MB)");
        }
    }

    private Map<String, Object> parseTechnicalInfo(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > config.getMaxTechnicalInfoSize().toBytes()) {
            throw new ServiceException("Technical info too large");
        }
        Map<String, Object> info;
        try {
            info = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JacksonException e) {
            throw new ServiceException("Technical info is not a valid JSON object");
        }
        if (info == null) {
            throw new ServiceException("Technical info is not a valid JSON object");
        }
        if (containsUnstorableText(info)) {
            throw new ServiceException("Technical info contains characters that cannot be stored");
        }
        return info.isEmpty() ? null : info;
    }

    private Feedback getFeedbackByIdOrThrow(UUID id) {
        Feedback feedback = database.find(Feedback.class)
                .setLabel("getFeedbackById")
                .where().idEq(id)
                .findOne();
        if (feedback == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found");
        }
        return feedback;
    }

    private List<FeedbackDocument> findFeedbackAttachments(UUID feedbackId) {
        return database.find(FeedbackDocument.class)
                .setLabel("findFeedbackAttachments")
                .fetch("document")
                .where().eq("feedback.id", feedbackId)
                .orderBy("id")
                .findList();
    }

    private DetailFeedbackDTO toDetailFeedbackDTO(Feedback feedback) {
        List<AttachmentFeedbackDTO> attachments = findFeedbackAttachments(feedback.getId()).stream()
                .map(FeedbackDocument::getDocument)
                .map(document -> AttachmentFeedbackDTO.from(document, documentService.getDocumentUrl(document)))
                .toList();
        return DetailFeedbackDTO.from(feedback, attachments);
    }

    private static boolean containsUnstorableText(Object value) {
        return switch (value) {
            case String text -> !StorableText.isStorable(text);
            case Map<?, ?> map -> map.entrySet().stream()
                    .anyMatch(entry -> containsUnstorableText(entry.getKey()) || containsUnstorableText(entry.getValue()));
            case Collection<?> items -> items.stream().anyMatch(FeedbackService::containsUnstorableText);
            case null, default -> false;
        };
    }
}
