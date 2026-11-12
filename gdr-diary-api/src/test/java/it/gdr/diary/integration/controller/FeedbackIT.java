package it.gdr.diary.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.integration.support.AbstractIntegrationTest;
import it.gdr.diary.integration.support.BlockingTransaction;
import it.gdr.diary.integration.support.TestJwt;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMultipartMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class FeedbackIT extends AbstractIntegrationTest {

    private static final AtomicInteger ADDRESSES = new AtomicInteger();
    private static final RequestPostProcessor ADMIN = TestJwt.as(TestJwt.OTHER_SUBJECT, TestJwt.OTHER_EMAIL, "admin");
    private static final RequestPostProcessor USER = TestJwt.as(TestJwt.USER_SUBJECT, TestJwt.USER_EMAIL, "user");
    private static final String TODAY = LocalDate.now(ZoneId.of("Europe/Rome")).toString();
    private static final String YESTERDAY = LocalDate.now(ZoneId.of("Europe/Rome")).minusDays(1).toString();

    private static RequestPostProcessor newAddress() {
        int number = ADDRESSES.incrementAndGet();
        String address = "10.%d.%d.%d".formatted((number >> 16) & 255, (number >> 8) & 255, number & 255);
        return request -> {
            request.setRemoteAddr(address);
            request.setServletPath(request.getRequestURI());
            return request;
        };
    }

    private static MockMultipartFile image(String name) {
        return new MockMultipartFile("files", name, MediaType.IMAGE_PNG_VALUE, PNG);
    }

    private static MockMultipartFile imageOfSize(String name, int size) {
        return new MockMultipartFile("files", name, MediaType.IMAGE_PNG_VALUE, Arrays.copyOf(PNG, size));
    }

    private MockMultipartMvcRequestBuilder form(RequestPostProcessor address, String type, String message, MockMultipartFile... files) {
        MockMultipartMvcRequestBuilder request = mvc.post().uri("/feedback").multipart().with(address)
                .param("type", type)
                .param("message", message)
                .param("email", "alira@example.com")
                .param("consent", "true");
        Arrays.stream(files).forEach(request::file);
        return request;
    }

    private MockMultipartMvcRequestBuilder form(MockMultipartFile... files) {
        return form(newAddress(), "idea", "Great tags", files);
    }

    private UUID sendFeedback(String type, String message, MockMultipartFile... files) {
        form(newAddress(), type, message, files).exchange();
        return jdbc.queryForObject("select id from feedback where message = ?", UUID.class, message);
    }

    private UUID sendFeedback(MockMultipartFile... files) {
        return sendFeedback("idea", "Great tags", files);
    }

    private void sendFromAddress(RequestPostProcessor address, int times) {
        IntStream.range(0, times).forEach(index -> form(address, "idea", "Message " + index).exchange());
    }

    private String storedStatus(UUID id) {
        return jdbc.queryForObject("select status from feedback where id = ?", String.class, id);
    }

    // ---- POST /feedback ----

    @Test
    void createFeedback_noToken_returnsCreated() {
        assertThat(form()).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createFeedback_noToken_returnsReceivedMessage() {
        assertThat(form()).bodyJson().extractingPath("$.message").isEqualTo("Feedback received");
    }

    @Test
    void createFeedback_noToken_savesNewFeedbackWithUpperCaseType() {
        UUID id = sendFeedback("bug", "The page does not turn");

        assertThat(jdbc.queryForMap("select type, status, email, privacy_accepted from feedback where id = ?", id))
                .containsEntry("type", "BUG")
                .containsEntry("status", "NEW")
                .containsEntry("email", "alira@example.com")
                .containsEntry("privacy_accepted", true);
    }

    @Test
    void createFeedback_noToken_recordsAnonymousCreator() {
        UUID id = sendFeedback();

        assertThat(jdbc.queryForObject("select created_by from feedback where id = ?", String.class, id)).isEqualTo("N/A");
    }

    @Test
    void createFeedback_userToken_returnsCreated() {
        assertThat(form().with(USER)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createFeedback_technicalInfo_storesItAsJson() {
        form(newAddress(), "bug", "With info").param("technicalInfo", "{\"browser\":\"Firefox 130\"}").exchange();

        assertThat(jdbc.queryForObject("select technical_info ->> 'browser' from feedback where message = 'With info'", String.class))
                .isEqualTo("Firefox 130");
    }

    @Test
    void createFeedback_technicalInfoNotAnObject_returnsBadRequestMessage() {
        assertThat(form().param("technicalInfo", "[1,2]"))
                .bodyJson().extractingPath("$.violations[0].message").isEqualTo("Technical info is not a valid JSON object");
    }

    @Test
    void createFeedback_twoImages_storesThemInFeedbackBucket() throws Exception {
        sendFeedback(image("one.png"), image("two.png"));

        assertThat(storedObjectKeys(FEEDBACK_BUCKET)).hasSize(2);
    }

    @Test
    void createFeedback_twoImages_storesNothingInDiaryBucket() throws Exception {
        sendFeedback(image("one.png"), image("two.png"));

        assertThat(storedObjectKeys()).isEmpty();
    }

    @Test
    void createFeedback_twoImages_linksBothDocumentsToFeedback() {
        UUID id = sendFeedback(image("one.png"), image("two.png"));

        assertThat(jdbc.queryForObject("select count(*) from feedback_document where feedback_id = ?", Integer.class, id)).isEqualTo(2);
    }

    @Test
    void createFeedback_twoImages_recordsFeedbackBucketOnDocuments() {
        sendFeedback(image("one.png"), image("two.png"));

        assertThat(jdbc.queryForList("select distinct bucket from document", String.class)).containsExactly(FEEDBACK_BUCKET);
    }

    @Test
    void createFeedback_trapFieldFilled_returnsCreated() {
        assertThat(form().param("website", "https://spam.example")).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createFeedback_trapFieldFilled_savesNothing() throws Exception {
        form(image("one.png")).param("website", "https://spam.example").exchange();

        assertThat(countRows("feedback") + countRows("document") + storedObjectKeys(FEEDBACK_BUCKET).size()).isZero();
    }

    @Test
    void createFeedback_consentMissing_returnsBadRequest() {
        MvcTestResult result = mvc.post().uri("/feedback").multipart().with(newAddress())
                .param("type", "idea").param("message", "Hi").param("email", "alira@example.com").exchange();

        assertThat(result).bodyJson().extractingPath("$.violations[0].field").isEqualTo("consent");
    }

    @Test
    void createFeedback_consentFalse_savesNothing() {
        mvc.post().uri("/feedback").multipart().with(newAddress())
                .param("type", "idea").param("message", "Hi").param("email", "alira@example.com").param("consent", "false").exchange();

        assertThat(countRows("feedback")).isZero();
    }

    @Test
    void createFeedback_missingEmail_reportsEmailAsViolationField() {
        MvcTestResult result = mvc.post().uri("/feedback").multipart().with(newAddress())
                .param("type", "idea").param("message", "Hi").param("consent", "true").exchange();

        assertThat(result).bodyJson().extractingPath("$.violations[0].field").isEqualTo("email");
    }

    @Test
    void createFeedback_messageOf2000Chars_isSaved() {
        UUID id = sendFeedback("idea", "a".repeat(2000));

        assertThat(jdbc.queryForObject("select char_length(message) from feedback where id = ?", Integer.class, id)).isEqualTo(2000);
    }

    @Test
    void createFeedback_messageWithEmoji_isSavedUnchanged() {
        UUID id = sendFeedback("love", "Grazie 🎲📜!");

        assertThat(jdbc.queryForObject("select message from feedback where id = ?", String.class, id)).isEqualTo("Grazie 🎲📜!");
    }

    @Test
    void createFeedback_fourImages_returnsTooManyFilesMessage() {
        assertThat(form(image("1.png"), image("2.png"), image("3.png"), image("4.png")))
                .bodyJson().extractingPath("$.violations[0].message").isEqualTo("Too many files (maximum 3)");
    }

    @Test
    void createFeedback_fourImages_storesNoFile() throws Exception {
        form(image("1.png"), image("2.png"), image("3.png"), image("4.png")).exchange();

        assertThat(storedObjectKeys(FEEDBACK_BUCKET)).isEmpty();
    }

    @Test
    void createFeedback_imagesOverTotalSize_returnsBadRequest() {
        int twoMegabytes = 2 * 1024 * 1024;

        assertThat(form(imageOfSize("1.png", twoMegabytes), imageOfSize("2.png", twoMegabytes), imageOfSize("3.png", twoMegabytes)))
                .bodyJson().extractingPath("$.violations[0].message").isEqualTo("Files too large (maximum total 5 MB)");
    }

    @Test
    void createFeedback_pdfInsteadOfImage_returnsUnsupportedFileType() {
        MockMultipartFile pdf = new MockMultipartFile("files", "doc.pdf", MediaType.APPLICATION_PDF_VALUE, PDF);

        assertThat(form(pdf)).bodyJson().extractingPath("$.violations[0].message").isEqualTo("Unsupported file type");
    }

    @Test
    void createFeedback_secondImageInvalid_removesFirstImageFromStorage() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("files", "doc.pdf", MediaType.APPLICATION_PDF_VALUE, PDF);

        form(image("one.png"), pdf).exchange();

        assertThat(storedObjectKeys(FEEDBACK_BUCKET)).isEmpty();
    }

    @Test
    void createFeedback_secondImageInvalid_savesNoRow() {
        MockMultipartFile pdf = new MockMultipartFile("files", "doc.pdf", MediaType.APPLICATION_PDF_VALUE, PDF);

        form(image("one.png"), pdf).exchange();

        assertThat(countRows("feedback") + countRows("feedback_document") + countRows("document")).isZero();
    }

    @Test
    void createFeedback_emptyFilePart_isIgnored() {
        UUID id = sendFeedback(new MockMultipartFile("files", "", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[0]));

        assertThat(jdbc.queryForObject("select count(*) from feedback_document where feedback_id = ?", Integer.class, id)).isZero();
    }

    @Test
    void createFeedback_technicalInfoOver2Kilobytes_returnsTooLargeMessage() {
        String technicalInfo = "{\"log\":\"" + "x".repeat(2048) + "\"}";

        assertThat(form().param("technicalInfo", technicalInfo))
                .bodyJson().extractingPath("$.violations[0].message").isEqualTo("Technical info too large");
    }

    @Test
    void createFeedback_declaredTypeDifferentFromContent_returnsBadRequest() {
        MockMultipartFile fake = new MockMultipartFile("files", "photo.jpg", MediaType.IMAGE_JPEG_VALUE, PNG);

        assertThat(form(fake)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createFeedback_jsonBody_isRejected() {
        MvcTestResult result = mvc.post().uri("/feedback").with(newAddress())
                .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"idea\"}").exchange();

        assertThat(result).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void createFeedback_apiDocs_requireNoToken() {
        assertThat(mvc.get().uri("/v3/api-docs")).bodyJson().doesNotHavePath("$.paths['/feedback'].post.security");
    }

    @Test
    void findAllFeedbacks_apiDocs_requireBearerToken() {
        assertThat(mvc.get().uri("/v3/api-docs")).bodyJson().extractingPath("$.paths['/feedback'].get.security[0]")
                .isEqualTo(Map.of("bearerAuth", List.of()));
    }

    @ParameterizedTest
    @CsvSource({"GET,/feedback/not-a-uuid", "PATCH,/feedback/not-a-uuid/read", "DELETE,/feedback/not-a-uuid"})
    void adminRoute_idNotUuid_returnsBadRequest(String method, String path) {
        assertThat(mvc.method(HttpMethod.valueOf(method)).uri(path).with(ADMIN)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    // ---- rate limit ----

    @Test
    void createFeedback_fifthRequestFromSameAddress_returnsCreated() {
        RequestPostProcessor address = newAddress();
        sendFromAddress(address, 4);

        assertThat(form(address, "idea", "Fifth")).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void createFeedback_sixthRequestFromSameAddress_returnsTooManyRequests() {
        RequestPostProcessor address = newAddress();
        sendFromAddress(address, 5);

        assertThat(form(address, "idea", "Sixth")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void createFeedback_sixthRequestFromSameAddress_returnsRetryAfterOfTheWindow() {
        RequestPostProcessor address = newAddress();
        sendFromAddress(address, 5);

        assertThat(form(address, "idea", "Sixth")).headers().hasValue(HttpHeaders.RETRY_AFTER, "3600");
    }

    @Test
    void createFeedback_sixthRequestFromSameAddress_returnsErrorInApiFormat() {
        RequestPostProcessor address = newAddress();
        sendFromAddress(address, 5);

        assertThat(form(address, "idea", "Sixth")).bodyJson().extractingPath("$.title").isEqualTo("TOO_MANY_REQUESTS");
    }

    @Test
    void createFeedback_sixthRequestFromSameAddress_savesNothing() {
        RequestPostProcessor address = newAddress();
        sendFromAddress(address, 5);

        form(address, "idea", "Sixth").exchange();

        assertThat(countRows("feedback")).isEqualTo(5);
    }

    @Test
    void createFeedback_invalidRequestsFromSameAddress_countTowardsTheLimit() {
        RequestPostProcessor address = newAddress();
        IntStream.range(0, 5).forEach(index -> mvc.post().uri("/feedback").multipart().with(address).param("type", "x").exchange());

        assertThat(form(address, "idea", "Valid")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void createFeedback_otherAddressAfterLimit_returnsCreated() {
        sendFromAddress(newAddress(), 5);

        assertThat(form()).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void findAllFeedbacks_manyAdminReadsFromSameAddress_areNotLimited() {
        RequestPostProcessor address = newAddress();
        IntStream.range(0, 6).forEach(index -> mvc.get().uri("/feedback").with(address).with(ADMIN).exchange());

        assertThat(mvc.get().uri("/feedback").with(address).with(ADMIN)).hasStatusOk();
    }

    // ---- admin routes: access ----

    @ParameterizedTest
    @CsvSource({"GET,/feedback", "GET,/feedback/7c1e0a52-3b6d-4f0e-9a41-2d8b5e6f7a10",
            "PATCH,/feedback/7c1e0a52-3b6d-4f0e-9a41-2d8b5e6f7a10/read",
            "DELETE,/feedback/7c1e0a52-3b6d-4f0e-9a41-2d8b5e6f7a10"})
    void adminRoute_noToken_returnsUnauthorized(String method, String path) {
        assertThat(mvc.method(HttpMethod.valueOf(method)).uri(path)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest
    @CsvSource({"GET,/feedback", "GET,/feedback/7c1e0a52-3b6d-4f0e-9a41-2d8b5e6f7a10",
            "PATCH,/feedback/7c1e0a52-3b6d-4f0e-9a41-2d8b5e6f7a10/archive",
            "DELETE,/feedback/7c1e0a52-3b6d-4f0e-9a41-2d8b5e6f7a10"})
    void adminRoute_userRole_returnsForbidden(String method, String path) {
        assertThat(mvc.method(HttpMethod.valueOf(method)).uri(path).with(USER)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void markFeedbackAsRead_userRole_keepsStatus() {
        UUID id = sendFeedback();

        mvc.patch().uri("/feedback/{id}/read", id).with(USER).exchange();

        assertThat(storedStatus(id)).isEqualTo("NEW");
    }

    @Test
    void deleteFeedback_userRole_keepsData() throws Exception {
        UUID id = sendFeedback(image("one.png"));

        mvc.delete().uri("/feedback/{id}", id).with(USER).exchange();

        assertThat(storedObjectKeys(FEEDBACK_BUCKET)).hasSize(1);
    }

    @Test
    void deleteFeedback_noToken_keepsRow() {
        UUID id = sendFeedback();

        mvc.delete().uri("/feedback/{id}", id).exchange();

        assertThat(countRows("feedback")).isEqualTo(1);
    }

    // ---- GET /feedback ----

    @Test
    void findAllFeedbacks_admin_listsAllFeedbackOfAnyone() {
        sendFeedback("idea", "First");
        form(newAddress(), "bug", "Second").with(USER).exchange();

        assertThat(mvc.get().uri("/feedback").with(ADMIN)).bodyJson().extractingPath("$.totalRows").isEqualTo(2);
    }

    @Test
    void findAllFeedbacks_noSort_listsNewestFirst() {
        UUID older = sendFeedback("idea", "Older");
        UUID newer = sendFeedback("bug", "Newer");
        jdbc.update("update feedback set created_at = now() - interval '2 days' where id = ?", older);

        assertThat(mvc.get().uri("/feedback").with(ADMIN)).bodyJson().extractingPath("$.list[*].id")
                .isEqualTo(List.of(newer.toString(), older.toString()));
    }

    @Test
    void findAllFeedbacks_typeFilter_listsOnlyThatType() {
        sendFeedback("idea", "An idea");
        sendFeedback("bug", "A bug");

        assertThat(mvc.get().uri("/feedback?type=BUG").with(ADMIN)).bodyJson().extractingPath("$.list[*].messagePreview")
                .isEqualTo(List.of("A bug"));
    }

    @Test
    void findAllFeedbacks_statusFilter_listsOnlyThatStatus() {
        UUID read = sendFeedback("idea", "Read one");
        sendFeedback("idea", "New one");
        mvc.patch().uri("/feedback/{id}/read", read).with(ADMIN).exchange();

        assertThat(mvc.get().uri("/feedback?status=NEW").with(ADMIN)).bodyJson().extractingPath("$.list[*].messagePreview")
                .isEqualTo(List.of("New one"));
    }

    @Test
    void findAllFeedbacks_emailFilterWithOtherCase_matchesEmail() {
        sendFeedback("idea", "From Alira");

        assertThat(mvc.get().uri("/feedback?email=ALIRA@EXAMPLE").with(ADMIN)).bodyJson().extractingPath("$.totalRows").isEqualTo(1);
    }

    @Test
    void findAllFeedbacks_createdTodayRange_includesTodayFeedback() {
        sendFeedback();

        assertThat(mvc.get().uri("/feedback?createdFrom={from}&createdTo={to}", TODAY, TODAY).with(ADMIN))
                .bodyJson().extractingPath("$.totalRows").isEqualTo(1);
    }

    @Test
    void findAllFeedbacks_createdToYesterday_excludesTodayFeedback() {
        sendFeedback();

        assertThat(mvc.get().uri("/feedback?createdTo={to}", YESTERDAY).with(ADMIN)).bodyJson().extractingPath("$.totalRows").isEqualTo(0);
    }

    @Test
    void findAllFeedbacks_createdFromAfterCreatedTo_returnsBadRequest() {
        assertThat(mvc.get().uri("/feedback?createdFrom={from}&createdTo={to}", TODAY, YESTERDAY).with(ADMIN))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllFeedbacks_unknownType_returnsBadRequest() {
        assertThat(mvc.get().uri("/feedback?type=PRAISE").with(ADMIN)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllFeedbacks_longMessage_returnsShortPreview() {
        sendFeedback("idea", "a".repeat(500));

        assertThat(mvc.get().uri("/feedback").with(ADMIN)).bodyJson().extractingPath("$.list[0].messagePreview")
                .asString().hasSize(120);
    }

    // ---- GET /feedback/{id} ----

    @Test
    void getFeedbackById_admin_returnsWholeMessage() {
        UUID id = sendFeedback("idea", "a".repeat(500));

        assertThat(mvc.get().uri("/feedback/{id}", id).with(ADMIN)).bodyJson().extractingPath("$.message").asString().hasSize(500);
    }

    @Test
    void getFeedbackById_feedbackWithImage_returnsAttachmentUrlOfFeedbackBucket() {
        UUID id = sendFeedback(image("screenshot.png"));

        assertThat(mvc.get().uri("/feedback/{id}", id).with(ADMIN)).bodyJson().extractingPath("$.attachments[0].url")
                .asString().contains("/" + FEEDBACK_BUCKET + "/");
    }

    @Test
    void getFeedbackById_feedbackWithImage_returnsOriginalFileName() {
        UUID id = sendFeedback(image("screenshot.png"));

        assertThat(mvc.get().uri("/feedback/{id}", id).with(ADMIN)).bodyJson().extractingPath("$.attachments[0].fileName")
                .isEqualTo("screenshot.png");
    }

    @Test
    void getFeedbackById_missingFeedback_returnsNotFound() {
        assertThat(mvc.get().uri("/feedback/{id}", UUID.randomUUID()).with(ADMIN)).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---- PATCH /feedback/{id}/... ----

    @ParameterizedTest
    @CsvSource({"read,READ", "answered,ANSWERED", "archive,ARCHIVED", "unread,NEW"})
    void changeFeedbackStatus_admin_storesNewStatus(String action, String status) {
        UUID id = sendFeedback();
        jdbc.update("update feedback set status = 'READ' where id = ?", id);

        mvc.patch().uri("/feedback/{id}/" + action, id).with(ADMIN).exchange();

        assertThat(storedStatus(id)).isEqualTo(status);
    }

    @Test
    void archiveFeedback_admin_returnsArchivedDetail() {
        UUID id = sendFeedback();

        assertThat(mvc.patch().uri("/feedback/{id}/archive", id).with(ADMIN)).bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
    }

    @Test
    void archiveFeedback_calledTwice_returnsOk() {
        UUID id = sendFeedback();
        mvc.patch().uri("/feedback/{id}/archive", id).with(ADMIN).exchange();

        assertThat(mvc.patch().uri("/feedback/{id}/archive", id).with(ADMIN)).hasStatusOk();
    }

    private MvcTestResult markAsReadWhileArchivedElsewhere(UUID id) {
        try (BlockingTransaction archive = BlockingTransaction.begin(jdbc)
                .execute("update feedback set status = 'ARCHIVED', version = version + 1 where id = ?", id)) {
            CompletableFuture<MvcTestResult> read = archive.startBlockedCall(
                    () -> mvc.patch().uri("/feedback/{id}/read", id).with(ADMIN).exchange());
            archive.commit();
            return read.join();
        }
    }

    @Test
    void markFeedbackAsRead_changedAtTheSameTime_returnsConflict() {
        UUID id = sendFeedback();

        MvcTestResult result = markAsReadWhileArchivedElsewhere(id);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void markFeedbackAsRead_changedAtTheSameTime_keepsTheOtherChange() {
        UUID id = sendFeedback();

        markAsReadWhileArchivedElsewhere(id);

        assertThat(storedStatus(id)).isEqualTo("ARCHIVED");
    }

    @Test
    void markFeedbackAsRead_missingFeedback_returnsNotFound() {
        assertThat(mvc.patch().uri("/feedback/{id}/read", UUID.randomUUID()).with(ADMIN)).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---- DELETE /feedback/{id} ----

    @Test
    void deleteFeedback_admin_returnsDeletedMessage() {
        UUID id = sendFeedback();

        assertThat(mvc.delete().uri("/feedback/{id}", id).with(ADMIN)).bodyJson().extractingPath("$.message").isEqualTo("Feedback deleted");
    }

    @Test
    void deleteFeedback_feedbackWithImages_deletesAllRows() {
        UUID id = sendFeedback(image("one.png"), image("two.png"));

        mvc.delete().uri("/feedback/{id}", id).with(ADMIN).exchange();

        assertThat(countRows("feedback") + countRows("feedback_document") + countRows("document")).isZero();
    }

    @Test
    void deleteFeedback_feedbackWithImages_removesFilesFromStorage() throws Exception {
        UUID id = sendFeedback(image("one.png"), image("two.png"));

        mvc.delete().uri("/feedback/{id}", id).with(ADMIN).exchange();

        assertThat(storedObjectKeys(FEEDBACK_BUCKET)).isEmpty();
    }

    @Test
    void deleteFeedback_otherFeedbackWithImage_keepsIt() throws Exception {
        UUID deleted = sendFeedback("idea", "Delete me", image("one.png"));
        sendFeedback("idea", "Keep me", image("two.png"));

        mvc.delete().uri("/feedback/{id}", deleted).with(ADMIN).exchange();

        assertThat(storedObjectKeys(FEEDBACK_BUCKET)).hasSize(1);
    }

    @Test
    void deleteFeedback_missingFeedback_returnsNotFound() {
        assertThat(mvc.delete().uri("/feedback/{id}", UUID.randomUUID()).with(ADMIN)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteAppUser_userWhoSentFeedback_keepsFeedback() {
        form().with(USER).exchange();
        createAppUser(TestJwt.authenticatedAt(Instant.now()));

        mvc.delete().uri("/me").with(TestJwt.authenticatedAt(Instant.now())).exchange();

        assertThat(countRows("feedback")).isEqualTo(1);
    }
}
