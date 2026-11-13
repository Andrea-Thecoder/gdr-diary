package it.gdr.diary.unit.services;

import static it.gdr.diary.unit.support.QueryStub.pagedList;
import static it.gdr.diary.unit.support.QueryStub.stubFind;
import static it.gdr.diary.unit.support.QueryStub.stubFindLeniently;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.ebean.Database;
import io.ebean.PagedList;
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
import org.mockito.InOrder;
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
    void findAllFeedbacks_anyRequest_labelsQuery() {
        QueryStub<Feedback> feedbacks = stubFind(database, Feedback.class);
        PagedList<Feedback> page = pagedList(List.of(), 0, 20, 0);
        when(feedbacks.where().findPagedList()).thenReturn(page);

        service.findAllFeedbacks(new FeedbackSearchRequest());

        verify(feedbacks.query()).setLabel("findAllFeedbacks");
    }

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

    @Test
    void findAllFeedbacks_noSort_ordersNewestFirst() {
        QueryStub<Feedback> feedbacks = stubFind(database, Feedback.class);
        PagedList<Feedback> page = pagedList(List.of(), 0, 20, 0);
        when(feedbacks.where().findPagedList()).thenReturn(page);

        service.findAllFeedbacks(new FeedbackSearchRequest());

        verify(feedbacks.where()).orderBy("createdAt desc, id asc");
    }

    @Test
    void findAllFeedbacks_feedbackFound_returnsSummaries() {
        QueryStub<Feedback> feedbacks = stubFind(database, Feedback.class);
        PagedList<Feedback> page = pagedList(List.of(feedback(FeedbackStatus.NEW)), 0, 20, 1);
        when(feedbacks.where().findPagedList()).thenReturn(page);

        PagedResultDTO<BaseFeedbackDTO> result = service.findAllFeedbacks(new FeedbackSearchRequest());

        assertThat(result.getList()).extracting(BaseFeedbackDTO::id, BaseFeedbackDTO::messagePreview)
                .containsExactly(tuple(FEEDBACK_ID, "Great tags"));
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
    void getFeedbackById_feedbackWithImages_returnsAttachmentsWithUrls() {
        Document first = document("one.png");
        Document second = document("two.png");
        givenFeedback(feedback(FeedbackStatus.NEW), first, second);
        when(documentService.getDocumentUrl(first)).thenReturn("https://storage.example/one");
        when(documentService.getDocumentUrl(second)).thenReturn("https://storage.example/two");

        DetailFeedbackDTO result = service.getFeedbackById(FEEDBACK_ID);

        assertThat(result.attachments()).extracting(AttachmentFeedbackDTO::fileName, AttachmentFeedbackDTO::url)
                .containsExactly(tuple("one.png", "https://storage.example/one"),
                        tuple("two.png", "https://storage.example/two"));
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
    void createFeedback_blankTrapField_savesFeedback() {
        Feedback saved = savedFeedback(request("idea", "Great tags", "alira@example.com", null, "  "));

        assertThat(saved.getMessage()).isEqualTo("Great tags");
    }

    @ParameterizedTest
    @ValueSource(strings = {"idea", "IDEA", " Idea "})
    void createFeedback_typeInAnyCase_savesUpperCaseType(String type) {
        Feedback saved = savedFeedback(request(type, "Great tags", "alira@example.com", null, null));

        assertThat(saved.getType()).isEqualTo(FeedbackType.IDEA);
    }

    @Test
    void createFeedback_newFeedback_isToBeRead() {
        Feedback saved = savedFeedback(request());

        assertThat(saved.getStatus()).isEqualTo(FeedbackStatus.NEW);
    }

    @Test
    void createFeedback_newFeedback_recordsPrivacyAcceptance() {
        Feedback saved = savedFeedback(request());

        assertThat(saved.isPrivacyAccepted()).isTrue();
    }

    @Test
    void createFeedback_messageWithSurroundingSpaces_savesTrimmedMessage() {
        Feedback saved = savedFeedback(request("love", "  Thank you!  ", "alira@example.com", null, null));

        assertThat(saved.getMessage()).isEqualTo("Thank you!");
    }

    @Test
    void createFeedback_emailWithSurroundingSpaces_savesTrimmedEmail() {
        Feedback saved = savedFeedback(request("idea", "Great tags", " alira@example.com ", null, null));

        assertThat(saved.getEmail()).isEqualTo("alira@example.com");
    }

    @Test
    void createFeedback_technicalInfoObject_savesItAsMap() {
        Feedback saved = savedFeedback(requestWithTechnicalInfo("{\"browser\":\"Firefox 130\",\"screen\":{\"width\":1360}}"));

        assertThat(saved.getTechnicalInfo()).isEqualTo(Map.of("browser", "Firefox 130", "screen", Map.of("width", 1360)));
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

    @Test
    void createFeedback_technicalInfoOverMaxSize_throwsServiceException() {
        config.setMaxTechnicalInfoSize(DataSize.ofBytes(20));

        assertThatThrownBy(() -> service.createFeedback(requestWithTechnicalInfo("{\"browser\":\"Firefox 130.0\"}"), List.of()))
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

    @Test
    void createFeedback_technicalInfoSizeInBytesNotChars_countsMultiByteCharacters() {
        config.setMaxTechnicalInfoSize(DataSize.ofBytes(20));

        assertThatThrownBy(() -> service.createFeedback(requestWithTechnicalInfo("{\"b\":\"àààààààà\"}"), List.of()))
                .hasMessage("Technical info too large");
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
    void createFeedback_tooManyFiles_throwsServiceException() {
        List<MultipartFile> files = List.of(png("1.png"), png("2.png"), png("3.png"), png("4.png"));

        assertThatThrownBy(() -> service.createFeedback(request(), files))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Too many files (maximum 3)");
    }

    @Test
    void createFeedback_tooManyFiles_savesNothing() {
        List<MultipartFile> files = List.of(png("1.png"), png("2.png"), png("3.png"), png("4.png"));

        catchThrowable(() -> service.createFeedback(request(), files));

        verifyNoInteractions(database, documentService);
    }

    @Test
    void createFeedback_emptyFileParts_areIgnored() {
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(any(MultipartFile.class), anyString())).thenReturn(document("x.png"));
        List<MultipartFile> files = List.of(png("1.png"), emptyFile(), png("2.png"), png("3.png"), emptyFile());

        service.createFeedback(request(), files);

        verify(documentService, times(3)).createImageDocument(any(MultipartFile.class), anyString());
    }

    @Test
    void createFeedback_filesOverTotalSize_throwsServiceException() {
        config.setMaxTotalFileSize(DataSize.ofBytes(15));
        List<MultipartFile> files = List.of(pngOfSize("1.png", 8), pngOfSize("2.png", 8));

        assertThatThrownBy(() -> service.createFeedback(request(), files))
                .isInstanceOf(ServiceException.class)
                .hasMessageStartingWith("Files too large");
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
    void createFeedback_withFiles_storesThemInFeedbackBucket() {
        MultipartFile file = png("one.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(file, BUCKET)).thenReturn(document("one.png"));

        service.createFeedback(request(), List.of(file));

        verify(documentService).createImageDocument(file, BUCKET);
    }

    @Test
    void createFeedback_withFiles_linksEachDocumentToFeedback() {
        Document first = document("one.png");
        Document second = document("two.png");
        MultipartFile firstFile = png("one.png");
        MultipartFile secondFile = png("two.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(firstFile, BUCKET)).thenReturn(first);
        when(documentService.createImageDocument(secondFile, BUCKET)).thenReturn(second);
        ArgumentCaptor<Object> saved = ArgumentCaptor.forClass(Object.class);

        service.createFeedback(request(), List.of(firstFile, secondFile));

        verify(database, times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).filteredOn(FeedbackDocument.class::isInstance)
                .extracting(link -> ((FeedbackDocument) link).getDocument())
                .containsExactly(first, second);
    }

    @Test
    void createFeedback_validRequest_commitsTransaction() {
        when(database.beginTransaction()).thenReturn(transaction);

        service.createFeedback(request(), List.of());

        verify(transaction).commit();
    }

    @Test
    void createFeedback_secondFileFails_deletesFirstUploadedFile() {
        Document first = document("one.png");
        MultipartFile firstFile = png("one.png");
        MultipartFile secondFile = png("two.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(firstFile, BUCKET)).thenReturn(first);
        when(documentService.createImageDocument(secondFile, BUCKET)).thenThrow(new ServiceException("Unsupported file type"));

        catchThrowable(() -> service.createFeedback(request(), List.of(firstFile, secondFile)));

        verify(documentService).deleteDocumentFile(first);
    }

    @Test
    void createFeedback_secondFileFails_rethrowsTheError() {
        MultipartFile firstFile = png("one.png");
        MultipartFile secondFile = png("two.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(firstFile, BUCKET)).thenReturn(document("one.png"));
        when(documentService.createImageDocument(secondFile, BUCKET)).thenThrow(new ServiceException("Unsupported file type"));

        assertThatThrownBy(() -> service.createFeedback(request(), List.of(firstFile, secondFile)))
                .hasMessage("Unsupported file type");
    }

    @Test
    void createFeedback_secondFileFails_doesNotCommit() {
        MultipartFile firstFile = png("one.png");
        MultipartFile secondFile = png("two.png");
        when(database.beginTransaction()).thenReturn(transaction);
        when(documentService.createImageDocument(firstFile, BUCKET)).thenReturn(document("one.png"));
        when(documentService.createImageDocument(secondFile, BUCKET)).thenThrow(new ServiceException("Unsupported file type"));

        catchThrowable(() -> service.createFeedback(request(), List.of(firstFile, secondFile)));

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
    void markFeedbackAsRead_newFeedback_savesReadStatus() {
        Feedback feedback = feedback(FeedbackStatus.NEW);
        givenFeedback(feedback);

        service.markFeedbackAsRead(FEEDBACK_ID);

        verify(database).save(argThat(saved -> saved == feedback && feedback.getStatus() == FeedbackStatus.READ));
    }

    @Test
    void markFeedbackAsRead_newFeedback_returnsReadStatus() {
        givenFeedback(feedback(FeedbackStatus.NEW));

        DetailFeedbackDTO result = service.markFeedbackAsRead(FEEDBACK_ID);

        assertThat(result.status()).isEqualTo(FeedbackStatus.READ);
    }

    @Test
    void markFeedbackAsUnread_readFeedback_returnsNewStatus() {
        givenFeedback(feedback(FeedbackStatus.READ));

        DetailFeedbackDTO result = service.markFeedbackAsUnread(FEEDBACK_ID);

        assertThat(result.status()).isEqualTo(FeedbackStatus.NEW);
    }

    @Test
    void markFeedbackAsAnswered_readFeedback_returnsAnsweredStatus() {
        givenFeedback(feedback(FeedbackStatus.READ));

        DetailFeedbackDTO result = service.markFeedbackAsAnswered(FEEDBACK_ID);

        assertThat(result.status()).isEqualTo(FeedbackStatus.ANSWERED);
    }

    @Test
    void archiveFeedback_answeredFeedback_returnsArchivedStatus() {
        givenFeedback(feedback(FeedbackStatus.ANSWERED));

        DetailFeedbackDTO result = service.archiveFeedback(FEEDBACK_ID);

        assertThat(result.status()).isEqualTo(FeedbackStatus.ARCHIVED);
    }

    @Test
    void archiveFeedback_missingFeedback_throwsNotFound() {
        givenMissingFeedback();

        assertThatThrownBy(() -> service.archiveFeedback(FEEDBACK_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void archiveFeedback_missingFeedback_savesNothing() {
        givenMissingFeedback();

        catchThrowable(() -> service.archiveFeedback(FEEDBACK_ID));

        verify(database, never()).save(any());
    }

    // ---- deleteFeedback ----

    @Test
    void deleteFeedback_missingFeedback_throwsNotFound() {
        givenMissingFeedback();

        assertThatThrownBy(() -> service.deleteFeedback(FEEDBACK_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteFeedback_missingFeedback_doesNotOpenTransaction() {
        givenMissingFeedback();

        catchThrowable(() -> service.deleteFeedback(FEEDBACK_ID));

        verify(database, never()).beginTransaction();
    }

    @Test
    void deleteFeedback_feedbackWithImages_deletesLinksBeforeFeedbackAndDocuments() {
        Feedback feedback = feedback(FeedbackStatus.NEW);
        Document image = document("one.png");
        givenFeedback(feedback, image);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteFeedback(FEEDBACK_ID);

        InOrder order = inOrder(database, documentService, transaction);
        order.verify(database).deleteAll(anyCollection());
        order.verify(database).delete(feedback);
        order.verify(documentService).deleteDocumentRecord(image);
        order.verify(transaction).commit();
    }

    @Test
    void deleteFeedback_feedbackWithImages_deletesFilesAfterCommit() {
        Document image = document("one.png");
        givenFeedback(feedback(FeedbackStatus.NEW), image);
        when(database.beginTransaction()).thenReturn(transaction);

        service.deleteFeedback(FEEDBACK_ID);

        InOrder order = inOrder(transaction, documentService);
        order.verify(transaction).commit();
        order.verify(documentService).deleteDocumentFile(image);
    }

    @Test
    void deleteFeedback_commitFails_deletesNoFile() {
        givenFeedback(feedback(FeedbackStatus.NEW), document("one.png"));
        when(database.beginTransaction()).thenReturn(transaction);
        doThrow(new PersistenceException("connection lost")).when(transaction).commit();

        catchThrowable(() -> service.deleteFeedback(FEEDBACK_ID));

        verify(documentService, never()).deleteDocumentFile(any());
    }
}
