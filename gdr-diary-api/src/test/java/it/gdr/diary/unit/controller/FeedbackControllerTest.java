package it.gdr.diary.unit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import it.gdr.diary.controller.FeedbackController;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.feedback.DetailFeedbackDTO;
import it.gdr.diary.dto.feedback.InsertFeedbackDTO;
import it.gdr.diary.dto.search.FeedbackSearchRequest;
import it.gdr.diary.exception.ExceptionMapper;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.model.FeedbackStatus;
import it.gdr.diary.model.FeedbackType;
import it.gdr.diary.services.FeedbackService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
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
    void createFeedback_validFields_returnsCreated() {
        assertThat(validFeedback()).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createFeedback_validFields_returnsReceivedMessage() {
        assertThat(validFeedback()).bodyJson().extractingPath("$.message").isEqualTo("Feedback received");
    }

    @Test
    void createFeedback_allFields_passesThemToService() {
        validFeedback().param("technicalInfo", "{\"browser\":\"Firefox\"}").exchange();

        assertThat(sentRequest()).extracting(InsertFeedbackDTO::type, InsertFeedbackDTO::message, InsertFeedbackDTO::email,
                        InsertFeedbackDTO::technicalInfo, InsertFeedbackDTO::consent)
                .containsExactly("idea", "Great tags", "alira@example.com", "{\"browser\":\"Firefox\"}", true);
    }

    @Test
    void createFeedback_twoFiles_passesBothToService() {
        validFeedback().file(png("one.png")).file(png("two.png")).exchange();

        ArgumentCaptor<List<MultipartFile>> files = ArgumentCaptor.captor();
        verify(service).createFeedback(any(), files.capture());
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
    void createFeedback_consentFalse_returnsPrivacyMessage() {
        assertThat(feedbackForm("idea", "Hi", "alira@example.com").param("consent", "false"))
                .bodyJson().extractingPath("$.violations[0].message").isEqualTo("the privacy policy must be accepted");
    }

    @Test
    void createFeedback_consentFalse_doesNotCallService() {
        feedbackForm("idea", "Hi", "alira@example.com").param("consent", "false").exchange();

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
    void createFeedback_missingEmail_reportsEmailAsViolationField() {
        assertThat(mvc.post().uri("/feedback").multipart().param("type", "idea").param("message", "Hi").param("consent", "true"))
                .bodyJson().extractingPath("$.violations[0].field").isEqualTo("email");
    }

    @Test
    void createFeedback_missingEmail_doesNotCallService() {
        mvc.post().uri("/feedback").multipart().param("type", "idea").param("message", "Hi").param("consent", "true").exchange();

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
    void findAllFeedbacks_createdFromAfterCreatedTo_returnsRangeMessage() {
        assertThat(mvc.get().uri("/feedback?createdFrom=2026-11-02&createdTo=2026-11-01"))
                .bodyJson().extractingPath("$.violations[0].message").isEqualTo("createdFrom must not be after createdTo");
    }

    @Test
    void findAllFeedbacks_createdFromAfterCreatedTo_doesNotCallService() {
        mvc.get().uri("/feedback?createdFrom=2026-11-02&createdTo=2026-11-01").exchange();

        verifyNoInteractions(service);
    }

    // ---- GET /feedback/{id} ----

    @Test
    void getFeedbackById_existingFeedback_returnsDetail() {
        when(service.getFeedbackById(FEEDBACK_ID)).thenReturn(detail(FeedbackStatus.NEW));

        assertThat(mvc.get().uri("/feedback/{id}", FEEDBACK_ID)).bodyJson().extractingPath("$.message").isEqualTo("Great tags");
    }

    @Test
    void getFeedbackById_notUuid_returnsBadRequest() {
        assertThat(mvc.get().uri("/feedback/not-a-uuid")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getFeedbackById_missingFeedback_returnsNotFound() {
        when(service.getFeedbackById(FEEDBACK_ID)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found"));

        assertThat(mvc.get().uri("/feedback/{id}", FEEDBACK_ID)).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---- PATCH /feedback/{id}/... ----

    @Test
    void markFeedbackAsRead_existingFeedback_returnsReadStatus() {
        when(service.markFeedbackAsRead(FEEDBACK_ID)).thenReturn(detail(FeedbackStatus.READ));

        assertThat(mvc.patch().uri("/feedback/{id}/read", FEEDBACK_ID)).bodyJson().extractingPath("$.status").isEqualTo("READ");
    }

    @Test
    void markFeedbackAsUnread_existingFeedback_returnsNewStatus() {
        when(service.markFeedbackAsUnread(FEEDBACK_ID)).thenReturn(detail(FeedbackStatus.NEW));

        assertThat(mvc.patch().uri("/feedback/{id}/unread", FEEDBACK_ID)).bodyJson().extractingPath("$.status").isEqualTo("NEW");
    }

    @Test
    void markFeedbackAsAnswered_existingFeedback_returnsAnsweredStatus() {
        when(service.markFeedbackAsAnswered(FEEDBACK_ID)).thenReturn(detail(FeedbackStatus.ANSWERED));

        assertThat(mvc.patch().uri("/feedback/{id}/answered", FEEDBACK_ID)).bodyJson().extractingPath("$.status").isEqualTo("ANSWERED");
    }

    @Test
    void archiveFeedback_existingFeedback_returnsArchivedStatus() {
        when(service.archiveFeedback(FEEDBACK_ID)).thenReturn(detail(FeedbackStatus.ARCHIVED));

        assertThat(mvc.patch().uri("/feedback/{id}/archive", FEEDBACK_ID)).bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
    }

    @Test
    void archiveFeedback_missingFeedback_returnsNotFound() {
        when(service.archiveFeedback(FEEDBACK_ID)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found"));

        assertThat(mvc.patch().uri("/feedback/{id}/archive", FEEDBACK_ID)).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---- DELETE /feedback/{id} ----

    @Test
    void deleteFeedback_existingFeedback_returnsDeletedMessage() {
        assertThat(mvc.delete().uri("/feedback/{id}", FEEDBACK_ID)).bodyJson().extractingPath("$.message").isEqualTo("Feedback deleted");
    }

    @Test
    void deleteFeedback_existingFeedback_deletesThroughService() {
        mvc.delete().uri("/feedback/{id}", FEEDBACK_ID).exchange();

        verify(service).deleteFeedback(FEEDBACK_ID);
    }

    @Test
    void deleteFeedback_missingFeedback_returnsNotFound() {
        doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Feedback not found")).when(service).deleteFeedback(FEEDBACK_ID);

        assertThat(mvc.delete().uri("/feedback/{id}", FEEDBACK_ID)).hasStatus(HttpStatus.NOT_FOUND);
    }
}
