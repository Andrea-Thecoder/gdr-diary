package it.liberheroum.unit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import it.liberheroum.controller.FeedbackController;
import it.liberheroum.dto.PagedResultDTO;
import it.liberheroum.dto.feedback.DetailFeedbackDTO;
import it.liberheroum.dto.feedback.InsertFeedbackDTO;
import it.liberheroum.dto.search.FeedbackSearchRequest;
import it.liberheroum.exception.ExceptionMapper;
import it.liberheroum.exception.ServiceException;
import it.liberheroum.model.FeedbackStatus;
import it.liberheroum.model.FeedbackType;
import it.liberheroum.services.FeedbackService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMultipartMvcRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class FeedbackControllerTest {

    private static final UUID FEEDBACK_ID = UUID.fromString("7c1e0a52-3b6d-4f0e-9a41-2d8b5e6f7a10");

    @Mock
    private FeedbackService service;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new FeedbackController(service))
                .setControllerAdvice(new ExceptionMapper())
                .build());
    }

    private static DetailFeedbackDTO detail(FeedbackStatus status) {
        return new DetailFeedbackDTO(FEEDBACK_ID, FeedbackType.IDEA, status, null, "Great tags", null, true,
                Instant.parse("2026-11-11T18:30:00Z"), List.of());
    }

    private static MockMultipartFile png(String name) {
        return new MockMultipartFile("files", name, MediaType.IMAGE_PNG_VALUE, new byte[]{(byte) 0x89, 'P', 'N', 'G'});
    }

    private MockMultipartMvcRequestBuilder feedbackForm(String type, String message, String email) {
        return mvc.post().uri("/feedback").multipart()
                .param("type", type)
                .param("message", message)
                .param("email", email);
    }

    private MockMultipartMvcRequestBuilder feedbackForm(String type, String message) {
        return feedbackForm(type, message, "alira@example.com").param("consent", "true");
    }

    private MockMultipartMvcRequestBuilder validFeedback() {
        return feedbackForm("idea", "Great tags");
    }

    private InsertFeedbackDTO sentRequest() {
        ArgumentCaptor<InsertFeedbackDTO> request = ArgumentCaptor.forClass(InsertFeedbackDTO.class);
        verify(service).createFeedback(request.capture(), any());
        return request.getValue();
    }

    // ---- POST /feedback ----

    @Test
    void createFeedback_allFieldsAndTwoFiles_returnsCreatedAndPassesThemToService() {
        MvcTestResult result = validFeedback().param("technicalInfo", "{\"browser\":\"Firefox\"}")
                .file(png("one.png")).file(png("two.png")).exchange();

        ArgumentCaptor<List<MultipartFile>> files = ArgumentCaptor.captor();
        ArgumentCaptor<InsertFeedbackDTO> request = ArgumentCaptor.forClass(InsertFeedbackDTO.class);
        verify(service).createFeedback(request.capture(), files.capture());
        assertThat(result).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.message").isEqualTo("Feedback received");
        assertThat(request.getValue()).extracting(InsertFeedbackDTO::type, InsertFeedbackDTO::message, InsertFeedbackDTO::email,
                        InsertFeedbackDTO::technicalInfo, InsertFeedbackDTO::consent)
                .containsExactly("idea", "Great tags", "alira@example.com", "{\"browser\":\"Firefox\"}", true);
        assertThat(files.getValue()).extracting(MultipartFile::getOriginalFilename).containsExactly("one.png", "two.png");
    }

    @ParameterizedTest
    @ValueSource(strings = {"idea", "BUG", "Love"})
    void createFeedback_knownTypeInAnyCase_isAccepted(String type) {
        assertThat(feedbackForm(type, "Hi"))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createFeedback_unknownType_reportsTypeAsViolationField() {
        assertThat(feedbackForm("praise", "Hi"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("type");
    }

    @Test
    void createFeedback_missingConsent_reportsConsentAsViolationField() {
        assertThat(feedbackForm("idea", "Hi", "alira@example.com"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("consent");
    }

    @Test
    void createFeedback_consentFalse_returnsPrivacyMessageAndDoesNotCallService() {
        MvcTestResult result = feedbackForm("idea", "Hi", "alira@example.com").param("consent", "false").exchange();

        assertThat(result).bodyJson().extractingPath("$.violations[0].message").isEqualTo("the privacy policy must be accepted");
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void createFeedback_blankMessage_reportsMessageAsViolationField(String message) {
        assertThat(feedbackForm("idea", message))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("message");
    }

    @Test
    void createFeedback_messageOf2000Chars_isAccepted() {
        assertThat(feedbackForm("idea", "a".repeat(2000)))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createFeedback_messageOf2001Chars_returnsBadRequest() {
        assertThat(feedbackForm("idea", "a".repeat(2001)))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "", "   "})
    void createFeedback_invalidOrBlankEmail_reportsEmailAsViolationField(String email) {
        assertThat(feedbackForm("idea", "Great tags", email).param("consent", "true"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("email");
    }

    @Test
    void createFeedback_missingEmail_reportsEmailAsViolationFieldAndDoesNotCallService() {
        MvcTestResult result = mvc.post().uri("/feedback").multipart().param("type", "idea").param("message", "Hi").param("consent", "true").exchange();

        assertThat(result).bodyJson().extractingPath("$.violations[0].field").isEqualTo("email");
        verifyNoInteractions(service);
    }

    @Test
    void createFeedback_messageWithNulCharacter_reportsMessageAsViolationField() {
        assertThat(feedbackForm("idea", "Hi\u0000"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("message");
    }

    @Test
    void createFeedback_serviceRejects_returnsBadRequestWithItsMessage() {
        doThrow(new ServiceException("Too many files (maximum 3)")).when(service).createFeedback(any(), anyList());

        assertThat(validFeedback().file(png("one.png")))
                .bodyJson().extractingPath("$.violations[0].message").isEqualTo("Too many files (maximum 3)");
    }

    // ---- GET /feedback ----

    @Test
    void findAllFeedbacks_queryParameters_bindsSearchRequest() {
        when(service.findAllFeedbacks(any())).thenReturn(PagedResultDTO.of(List.of(), 0, 1, 20));

        mvc.get().uri("/feedback?type=BUG&status=NEW&email=example.com&createdFrom=2026-11-01&createdTo=2026-11-30").exchange();

        ArgumentCaptor<FeedbackSearchRequest> request = ArgumentCaptor.forClass(FeedbackSearchRequest.class);
        verify(service).findAllFeedbacks(request.capture());
        assertThat(request.getValue()).extracting(FeedbackSearchRequest::getType, FeedbackSearchRequest::getStatus,
                        FeedbackSearchRequest::getEmail,
                        FeedbackSearchRequest::getCreatedFrom, FeedbackSearchRequest::getCreatedTo)
                .containsExactly(FeedbackType.BUG, FeedbackStatus.NEW, "example.com",
                        LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-30"));
    }

    @Test
    void findAllFeedbacks_noParameters_sortsNewestFirst() {
        when(service.findAllFeedbacks(any())).thenReturn(PagedResultDTO.of(List.of(), 0, 1, 20));

        mvc.get().uri("/feedback").exchange();

        ArgumentCaptor<FeedbackSearchRequest> request = ArgumentCaptor.forClass(FeedbackSearchRequest.class);
        verify(service).findAllFeedbacks(request.capture());
        assertThat(request.getValue().isDescending()).isTrue();
    }

    @Test
    void findAllFeedbacks_unknownStatus_reportsStatusAsViolationField() {
        assertThat(mvc.get().uri("/feedback?status=DONE"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("status");
    }

    @Test
    void findAllFeedbacks_dateWithTime_reportsCreatedFromAsViolationField() {
        assertThat(mvc.get().uri("/feedback?createdFrom=2026-11-01T10:00:00"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("createdFrom");
    }

    @Test
    void findAllFeedbacks_createdFromAfterCreatedTo_returnsRangeMessageAndDoesNotCallService() {
        MvcTestResult result = mvc.get().uri("/feedback?createdFrom=2026-11-02&createdTo=2026-11-01").exchange();

        assertThat(result).bodyJson().extractingPath("$.violations[0].message").isEqualTo("createdFrom must not be after createdTo");
        verifyNoInteractions(service);
    }

    // ---- /feedback/{id} ----

    @Test
    void getFeedbackById_existingFeedback_returnsDetail() {
        when(service.getFeedbackById(FEEDBACK_ID)).thenReturn(detail(FeedbackStatus.NEW));

        assertThat(mvc.get().uri("/feedback/{id}", FEEDBACK_ID)).bodyJson().extractingPath("$.message").isEqualTo("Great tags");
    }

    @ParameterizedTest
    @CsvSource({"GET,/feedback/not-a-uuid", "PATCH,/feedback/not-a-uuid/read", "DELETE,/feedback/not-a-uuid"})
    void adminRoute_idNotUuid_returnsBadRequestWithoutCallingService(String method, String path) {
        MvcTestResult result = mvc.method(HttpMethod.valueOf(method)).uri(path).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void getFeedbackById_missingFeedback_returnsNotFound() {
        when(service.getFeedbackById(FEEDBACK_ID)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found"));

        assertThat(mvc.get().uri("/feedback/{id}", FEEDBACK_ID)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void changeFeedbackStatus_everyAction_returnsDetailWithNewStatus() {
        when(service.markFeedbackAsRead(FEEDBACK_ID)).thenReturn(detail(FeedbackStatus.READ));
        when(service.markFeedbackAsUnread(FEEDBACK_ID)).thenReturn(detail(FeedbackStatus.NEW));
        when(service.markFeedbackAsAnswered(FEEDBACK_ID)).thenReturn(detail(FeedbackStatus.ANSWERED));
        when(service.archiveFeedback(FEEDBACK_ID)).thenReturn(detail(FeedbackStatus.ARCHIVED));

        MvcTestResult read = mvc.patch().uri("/feedback/{id}/read", FEEDBACK_ID).exchange();
        MvcTestResult unread = mvc.patch().uri("/feedback/{id}/unread", FEEDBACK_ID).exchange();
        MvcTestResult answered = mvc.patch().uri("/feedback/{id}/answered", FEEDBACK_ID).exchange();
        MvcTestResult archived = mvc.patch().uri("/feedback/{id}/archive", FEEDBACK_ID).exchange();

        assertThat(read).bodyJson().extractingPath("$.status").isEqualTo("READ");
        assertThat(unread).bodyJson().extractingPath("$.status").isEqualTo("NEW");
        assertThat(answered).bodyJson().extractingPath("$.status").isEqualTo("ANSWERED");
        assertThat(archived).bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
    }

    @Test
    void deleteFeedback_existingFeedback_returnsDeletedMessageAndDeletesThroughService() {
        MvcTestResult result = mvc.delete().uri("/feedback/{id}", FEEDBACK_ID).exchange();

        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("Feedback deleted");
        verify(service).deleteFeedback(FEEDBACK_ID);
    }
}
