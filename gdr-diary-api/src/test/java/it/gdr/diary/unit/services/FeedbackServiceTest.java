package it.gdr.diary.unit.services;

import static it.gdr.diary.unit.support.QueryStub.pagedList;
import static it.gdr.diary.unit.support.QueryStub.stubFind;
import static it.gdr.diary.unit.support.QueryStub.stubFindLeniently;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ebean.Database;
import io.ebean.PagedList;
import io.ebean.Transaction;
import it.gdr.diary.config.FeedbackConfig;
import it.gdr.diary.dto.feedback.DetailFeedbackDTO;
import it.gdr.diary.dto.feedback.InsertFeedbackDTO;
import it.gdr.diary.dto.search.FeedbackSearchRequest;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.model.Document;
import it.gdr.diary.model.Feedback;
import it.gdr.diary.model.FeedbackDocument;
import it.gdr.diary.model.FeedbackStatus;
import it.gdr.diary.model.FeedbackType;
import it.gdr.diary.services.DocumentService;
import it.gdr.diary.services.FeedbackService;
import it.gdr.diary.unit.support.QueryStub;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    private static final UUID FEEDBACK_ID = UUID.fromString("7c1e0a52-3b6d-4f0e-9a41-2d8b5e6f7a10");
    private static final String BUCKET = "feedback-documents";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Mock
    private Database database;
    @Mock
    private DocumentService documentService;
    @Mock
    private Transaction transaction;

    private final FeedbackConfig config = new FeedbackConfig();

    private FeedbackService service;

    @BeforeEach
    void setUp() {
        config.setBucket(BUCKET);
        service = new FeedbackService(database, documentService, config, JsonMapper.builder().build());
    }

    private static InsertFeedbackDTO request(String type, String message, String email, String technicalInfo, String website) {
        return new InsertFeedbackDTO(type, message, email, technicalInfo, true, website);
    }

    private static InsertFeedbackDTO request() {
        return request("idea", "Great tags", "alira@example.com", null, null);
    }

    private static InsertFeedbackDTO requestWithTechnicalInfo(String technicalInfo) {
        return request("bug", "Broken page", "alira@example.com", technicalInfo, null);
    }

    private static MultipartFile png(String name) {
        return new MockMultipartFile("files", name, "image/png", PNG);
    }

    private static MultipartFile emptyFile() {
        return new MockMultipartFile("files", "", "application/octet-stream", new byte[0]);
    }

    private static MultipartFile pngOfSize(String name, int size) {
        return new MockMultipartFile("files", name, "image/png", Arrays.copyOf(PNG, size));
    }

    private static Document document(String name) {
        Document document = new Document();
        document.setId(UUID.randomUUID());
        document.setName(name);
        document.setMimeType("image/png");
        document.setSize(482113L);
        document.setBucket(BUCKET);
        document.setObjectKey(UUID.randomUUID().toString());
        return document;
    }

    private static Feedback feedback(FeedbackStatus status) {
        Feedback feedback = new Feedback();
        feedback.setId(FEEDBACK_ID);
        feedback.setType(FeedbackType.IDEA);
        feedback.setStatus(status);
        feedback.setMessage("Great tags");
        feedback.setPrivacyAccepted(true);
        feedback.setCreatedAt(Instant.parse("2026-11-11T18:30:00Z"));
        return feedback;
    }

    private static FeedbackDocument link(Feedback feedback, Document document) {
        FeedbackDocument link = new FeedbackDocument();
        link.setFeedback(feedback);
        link.setDocument(document);
        return link;
    }

    private QueryStub<Feedback> givenFeedback(Feedback feedback, Document... attachments) {
        QueryStub<Feedback> feedbacks = stubFind(database, Feedback.class);
        when(feedbacks.where().findOne()).thenReturn(feedback);
        QueryStub<FeedbackDocument> links = stubFindLeniently(database, FeedbackDocument.class);
        when(links.where().findList()).thenReturn(Arrays.stream(attachments).map(document -> link(feedback, document)).toList());
        return feedbacks;
    }

    private void givenMissingFeedback() {
        QueryStub<Feedback> feedbacks = stubFind(database, Feedback.class);
        when(feedbacks.where().findOne()).thenReturn(null);
    }

    private Feedback savedFeedback(InsertFeedbackDTO request) {
        when(database.beginTransaction()).thenReturn(transaction);
        service.createFeedback(request, List.of());
        ArgumentCaptor<Feedback> saved = ArgumentCaptor.forClass(Feedback.class);
        verify(database).save(saved.capture());
        return saved.getValue();
    }

    // ---- findAllFeedbacks ----

    @Test
    void findAllFeedbacks_createdFrom_usesConfiguredTimezone() {
        config.setTimezone(ZoneId.of("UTC"));
        QueryStub<Feedback> feedbacks = stubFind(database, Feedback.class);
        PagedList<Feedback> page = pagedList(List.of(), 0, 20, 0);
        when(feedbacks.where().findPagedList()).thenReturn(page);
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setCreatedFrom(LocalDate.parse("2026-11-01"));

        service.findAllFeedbacks(request);

        verify(feedbacks.where()).ge("createdAt", Instant.parse("2026-11-01T00:00:00Z"));
    }

    // ---- getFeedbackById ----

    @Test
    void getFeedbackById_missingFeedback_throwsNotFound() {
        givenMissingFeedback();

        assertThatThrownBy(() -> service.getFeedbackById(FEEDBACK_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getReason()).isEqualTo("Feedback not found");
                });
    }

    @Test
    void getFeedbackById_feedbackWithoutImages_returnsEmptyAttachments() {
        givenFeedback(feedback(FeedbackStatus.NEW));

        DetailFeedbackDTO result = service.getFeedbackById(FEEDBACK_ID);

        assertThat(result.attachments()).isEmpty();
    }

    // ---- createFeedback ----

    @Test
    void createFeedback_trapFieldFilled_savesNothing() {
        service.createFeedback(request("idea", "Buy now", "alira@example.com", null, "https://spam.example"), List.of(png("one.png")));

        verifyNoInteractions(database, documentService);
    }

    @Test
    void createFeedback_validRequest_savesNewFeedbackWithTrimmedFieldsAndTechnicalInfo() {
        InsertFeedbackDTO request = request("idea", "  Thank you!  ", " alira@example.com ",
                "{\"browser\":\"Firefox 130\",\"screen\":{\"width\":1360}}", "  ");

        Feedback saved = savedFeedback(request);

        assertThat(saved).extracting(Feedback::getType, Feedback::getStatus, Feedback::isPrivacyAccepted, Feedback::getMessage,
                        Feedback::getEmail, Feedback::getTechnicalInfo)
                .containsExactly(FeedbackType.IDEA, FeedbackStatus.NEW, true, "Thank you!", "alira@example.com",
                        Map.of("browser", "Firefox 130", "screen", Map.of("width", 1360)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"idea", "IDEA", " Idea "})
    void createFeedback_typeInAnyCase_savesUpperCaseType(String type) {
        Feedback saved = savedFeedback(request(type, "Great tags", "alira@example.com", null, null));

        assertThat(saved.getType()).isEqualTo(FeedbackType.IDEA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "{}"})
    void createFeedback_blankOrEmptyTechnicalInfo_savesNoTechnicalInfo(String technicalInfo) {
        Feedback saved = savedFeedback(requestWithTechnicalInfo(technicalInfo));

        assertThat(saved.getTechnicalInfo()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "[1,2]", "\"text\"", "42", "null", "{\"browser\":"})
    void createFeedback_technicalInfoNotAnObject_throwsServiceException(String technicalInfo) {
        assertThatThrownBy(() -> service.createFeedback(requestWithTechnicalInfo(technicalInfo), List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Technical info is not a valid JSON object");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"browser\":\"Firefox 130.0\"}", "{\"b\":\"àààààààà\"}"})
    void createFeedback_technicalInfoOverMaxSizeInBytes_throwsServiceException(String technicalInfo) {
        config.setMaxTechnicalInfoSize(DataSize.ofBytes(20));

        assertThatThrownBy(() -> service.createFeedback(requestWithTechnicalInfo(technicalInfo), List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Technical info too large");
    }

    @Test
    void createFeedback_technicalInfoExactlyAtMaxSize_isAccepted() {
        String technicalInfo = "{\"browser\":\"Firefox\"}";
        config.setMaxTechnicalInfoSize(DataSize.ofBytes(technicalInfo.length()));

        Feedback saved = savedFeedback(requestWithTechnicalInfo(technicalInfo));

        assertThat(saved.getTechnicalInfo()).containsEntry("browser", "Firefox");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"page\":\"a\\u0000b\"}", "{\"a\\u0000\":\"x\"}", "{\"list\":[\"\\ud800\"]}", "{\"nested\":{\"x\":\"\\udc00\"}}"})
    void createFeedback_technicalInfoWithUnstorableText_throwsServiceException(String technicalInfo) {
        assertThatThrownBy(() -> service.createFeedback(requestWithTechnicalInfo(technicalInfo), List.of()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Technical info contains characters that cannot be stored");
    }

    @Test
    void createFeedback_invalidTechnicalInfo_savesNothing() {
        catchThrowable(() -> service.createFeedback(requestWithTechnicalInfo("not json"), List.of(png("one.png"))));

        verifyNoInteractions(database, documentService);
    }

    @Test
    void createFeedback_nullFiles_savesFeedbackWithoutDocuments() {
        when(database.beginTransaction()).thenReturn(transaction);

        service.createFeedback(request(), null);

        verifyNoInteractions(documentService);
    }

    @Test
    void createFeedback_tooManyFiles_throwsServiceExceptionAndSavesNothing() {
        List<MultipartFile> files = List.of(png("1.png"), png("2.png"), png("3.png"), png("4.png"));

        Throwable thrown = catchThrowable(() -> service.createFeedback(request(), files));

        assertThat(thrown).isInstanceOf(ServiceException.class).hasMessage("Too many files (maximum 3)");
        verifyNoInteractions(database, documentService);
    }

    @Test
    void createFeedback_filesExactlyAtTotalSize_areAccepted() {
        config.setMaxTotalFileSize(DataSize.ofBytes(16));
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(any(MultipartFile.class), anyString())).thenReturn(document("x.png"));

        service.createFeedback(request(), List.of(pngOfSize("1.png", 8), pngOfSize("2.png", 8)));

        verify(transaction).commit();
    }

    @Test
    void createFeedback_secondFileFails_rethrowsDeletesFirstFileAndDoesNotCommit() {
        Document first = document("one.png");
        MultipartFile firstFile = png("one.png");
        MultipartFile secondFile = png("two.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(firstFile, BUCKET)).thenReturn(first);
        when(documentService.createImageDocument(secondFile, BUCKET)).thenThrow(new ServiceException("Unsupported file type"));

        Throwable thrown = catchThrowable(() -> service.createFeedback(request(), List.of(firstFile, secondFile)));

        assertThat(thrown).hasMessage("Unsupported file type");
        verify(documentService).deleteDocumentFile(first);
        verify(transaction, never()).commit();
    }

    @Test
    void createFeedback_commitFails_deletesAllUploadedFiles() {
        Document first = document("one.png");
        Document second = document("two.png");
        MultipartFile firstFile = png("one.png");
        MultipartFile secondFile = png("two.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(firstFile, BUCKET)).thenReturn(first);
        when(documentService.createImageDocument(secondFile, BUCKET)).thenReturn(second);
        doThrow(new PersistenceException("connection lost")).when(transaction).commit();

        catchThrowable(() -> service.createFeedback(request(), List.of(firstFile, secondFile)));

        verify(documentService).deleteDocumentFile(first);
        verify(documentService).deleteDocumentFile(second);
    }

    // ---- status changes ----

    @Test
    void markFeedbackAsRead_newFeedback_returnsReadStatus() {
        givenFeedback(feedback(FeedbackStatus.NEW));

        DetailFeedbackDTO result = service.markFeedbackAsRead(FEEDBACK_ID);

        assertThat(result.status()).isEqualTo(FeedbackStatus.READ);
    }

    // ---- deleteFeedback ----

    @Test
    void deleteFeedback_commitFails_deletesNoFile() {
        givenFeedback(feedback(FeedbackStatus.NEW), document("one.png"));
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(new PersistenceException("connection lost")).when(transaction).commit();

        catchThrowable(() -> service.deleteFeedback(FEEDBACK_ID));

        verify(documentService, never()).deleteDocumentFile(any());
    }
}
