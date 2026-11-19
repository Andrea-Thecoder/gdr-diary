package it.liberheroum.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import it.liberheroum.integration.support.AbstractIntegrationTest;
import it.liberheroum.integration.support.BlockingTransaction;
import it.liberheroum.integration.support.TestJwt;
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
    void createFeedback_noToken_savesNewAnonymousFeedbackWithUpperCaseType() {
        MvcTestResult result = form(newAddress(), "bug", "The page does not turn").exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.message").isEqualTo("Feedback received");
        assertThat(jdbc.queryForMap("select type, status, email, privacy_accepted, created_by from feedback where message = ?",
                "The page does not turn"))
                .containsEntry("type", "BUG")
                .containsEntry("status", "NEW")
                .containsEntry("email", "alira@example.com")
                .containsEntry("privacy_accepted", true)
                .containsEntry("created_by", "N/A");
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
    void createFeedback_twoImages_storesFilesInFeedbackBucketAndLinksDocumentsToFeedback() throws Exception {
        UUID id = sendFeedback(image("one.png"), image("two.png"));

        assertThat(storedObjectKeys(FEEDBACK_BUCKET)).hasSize(2);
        assertThat(storedObjectKeys()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from feedback_document where feedback_id = ?", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForList("select distinct bucket from document", String.class)).containsExactly(FEEDBACK_BUCKET);
    }

    @Test
    void createFeedback_trapFieldFilled_returnsCreatedAndSavesNothing() throws Exception {
        MvcTestResult result = form(image("one.png")).param("website", "https://spam.example").exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(countRows("feedback") + countRows("document") + storedObjectKeys(FEEDBACK_BUCKET).size()).isZero();
    }

    @Test
    void createFeedback_consentMissing_returnsBadRequestOnConsentAndSavesNothing() {
        MvcTestResult result = mvc.post().uri("/feedback").multipart().with(newAddress())
                .param("type", "idea").param("message", "Hi").param("email", "alira@example.com").exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.violations[0].field").isEqualTo("consent");
        assertThat(countRows("feedback")).isZero();
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
    void createFeedback_fourImages_returnsTooManyFilesMessageAndStoresNoFile() throws Exception {
        MvcTestResult result = form(image("1.png"), image("2.png"), image("3.png"), image("4.png")).exchange();

        assertThat(result).bodyJson().extractingPath("$.violations[0].message").isEqualTo("Too many files (maximum 3)");
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
    void createFeedback_secondImageInvalid_removesFirstImageFromStorageAndSavesNoRow() throws Exception {
        MockMultipartFile pdf = new MockMultipartFile("files", "doc.pdf", MediaType.APPLICATION_PDF_VALUE, PDF);

        form(image("one.png"), pdf).exchange();

        assertThat(storedObjectKeys(FEEDBACK_BUCKET)).isEmpty();
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

    // ---- rate limit ----

    @Test
    void createFeedback_sixthRequestFromSameAddress_isBlockedWithRetryAfterAndNothingSaved() {
        RequestPostProcessor address = newAddress();
        sendFromAddress(address, 4);

        MvcTestResult fifth = form(address, "idea", "Fifth").exchange();
        MvcTestResult sixth = form(address, "idea", "Sixth").exchange();

        assertThat(fifth).hasStatus(HttpStatus.CREATED);
        assertThat(sixth).hasStatus(HttpStatus.TOO_MANY_REQUESTS).headers().hasValue(HttpHeaders.RETRY_AFTER, "3600");
        assertThat(sixth).bodyJson().extractingPath("$.title").isEqualTo("TOO_MANY_REQUESTS");
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
    void adminChanges_userRole_keepStatusAndData() throws Exception {
        UUID id = sendFeedback(image("one.png"));

        mvc.patch().uri("/feedback/{id}/read", id).with(USER).exchange();
        mvc.delete().uri("/feedback/{id}", id).with(USER).exchange();

        assertThat(storedStatus(id)).isEqualTo("NEW");
        assertThat(storedObjectKeys(FEEDBACK_BUCKET)).hasSize(1);
    }

    @Test
    void adminChanges_noToken_keepStatusAndData() {
        UUID id = sendFeedback();

        mvc.patch().uri("/feedback/{id}/read", id).exchange();
        mvc.delete().uri("/feedback/{id}", id).exchange();

        assertThat(storedStatus(id)).isEqualTo("NEW");
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
    void getFeedbackById_feedbackWithImage_returnsAttachmentWithBucketUrlAndOriginalFileName() {
        UUID id = sendFeedback(image("screenshot.png"));

        MvcTestResult result = mvc.get().uri("/feedback/{id}", id).with(ADMIN).exchange();

        assertThat(result).bodyJson().extractingPath("$.attachments[0].url").asString().contains("/" + FEEDBACK_BUCKET + "/");
        assertThat(result).bodyJson().extractingPath("$.attachments[0].fileName").isEqualTo("screenshot.png");
    }

    @ParameterizedTest
    @CsvSource({"GET,/feedback/{id}", "PATCH,/feedback/{id}/read", "DELETE,/feedback/{id}"})
    void adminRoute_missingFeedback_returnsNotFound(String method, String path) {
        assertThat(mvc.method(HttpMethod.valueOf(method)).uri(path, UUID.randomUUID()).with(ADMIN)).hasStatus(HttpStatus.NOT_FOUND);
    }

    // ---- PATCH /feedback/{id}/... ----

    @ParameterizedTest
    @CsvSource({"read,READ", "answered,ANSWERED", "archive,ARCHIVED", "unread,NEW"})
    void changeFeedbackStatus_admin_returnsAndStoresNewStatus(String action, String status) {
        UUID id = sendFeedback();
        jdbc.update("update feedback set status = 'READ' where id = ?", id);

        MvcTestResult result = mvc.patch().uri("/feedback/{id}/" + action, id).with(ADMIN).exchange();

        assertThat(List.of(json(result, "$.status"), storedStatus(id))).containsExactly(status, status);
    }

    @Test
    void archiveFeedback_calledTwice_returnsArchivedDetailBothTimes() {
        UUID id = sendFeedback();

        MvcTestResult first = mvc.patch().uri("/feedback/{id}/archive", id).with(ADMIN).exchange();
        MvcTestResult second = mvc.patch().uri("/feedback/{id}/archive", id).with(ADMIN).exchange();

        assertThat(first).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
        assertThat(second).hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
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
    void markFeedbackAsRead_changedAtTheSameTime_returnsConflictAndKeepsTheOtherChange() {
        UUID id = sendFeedback();

        MvcTestResult result = markAsReadWhileArchivedElsewhere(id);

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(storedStatus(id)).isEqualTo("ARCHIVED");
    }

    // ---- DELETE /feedback/{id} ----

    @Test
    void deleteFeedback_feedbackWithImages_returnsDeletedMessageAndRemovesRowsAndFiles() throws Exception {
        UUID id = sendFeedback(image("one.png"), image("two.png"));

        MvcTestResult result = mvc.delete().uri("/feedback/{id}", id).with(ADMIN).exchange();

        assertThat(result).bodyJson().extractingPath("$.message").isEqualTo("Feedback deleted");
        assertThat(countRows("feedback") + countRows("feedback_document") + countRows("document")).isZero();
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
    void deleteAppUser_userWhoSentFeedback_keepsFeedback() {
        form().with(USER).exchange();
        createAppUser(TestJwt.authenticatedAt(Instant.now()));

        mvc.delete().uri("/me").with(TestJwt.authenticatedAt(Instant.now())).exchange();

        assertThat(countRows("feedback")).isEqualTo(1);
    }
}
