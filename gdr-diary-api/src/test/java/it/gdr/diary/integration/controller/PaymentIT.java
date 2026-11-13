package it.gdr.diary.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import it.gdr.diary.integration.support.AbstractIntegrationTest;
import it.gdr.diary.integration.support.TestJwt;
import it.gdr.diary.model.AppUser;
import it.gdr.diary.model.Payment;
import it.gdr.diary.model.PaymentProvider;
import it.gdr.diary.model.PaymentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class PaymentIT extends AbstractIntegrationTest {

    private static final RequestPostProcessor USER = TestJwt.as(TestJwt.USER_SUBJECT, TestJwt.USER_EMAIL, "user");
    private static final RequestPostProcessor ADMIN = TestJwt.as(TestJwt.USER_SUBJECT, TestJwt.USER_EMAIL, "admin");
    private static final RequestPostProcessor NO_ROLES = TestJwt.as(TestJwt.USER_SUBJECT, TestJwt.USER_EMAIL);
    private static final Instant THREE_DAYS_AGO = Instant.now().minus(3, ChronoUnit.DAYS);
    private static final Instant YESTERDAY = Instant.now().minus(1, ChronoUnit.DAYS);
    private static final String ANY_PAYMENT = "9a3c5e71-4d2b-4f68-8b0e-6c1d7a2f3e45";

    private AppUser createUser(String subject, RequestPostProcessor user) {
        createAppUser(user);
        return database.find(AppUser.class).where().eq("keycloakId", subject).findOne();
    }

    private AppUser createCurrentUser() {
        return createUser(TestJwt.USER_SUBJECT, USER);
    }

    private AppUser createOtherUser() {
        return createUser(TestJwt.OTHER_SUBJECT, TestJwt.otherUser());
    }

    private Payment savePayment(AppUser owner, String description, PaymentStatus status, long amountCents, Instant paidAt) {
        Payment payment = new Payment();
        payment.setAppUser(owner);
        payment.setProvider(PaymentProvider.STRIPE);
        payment.setProviderPaymentId("pi_" + UUID.randomUUID());
        payment.setStatus(status);
        payment.setAmountCents(amountCents);
        payment.setCurrency("EUR");
        payment.setDescription(description);
        payment.setPaidAt(paidAt);
        payment.setCustomerEmail("frodo@shire.example");
        payment.setCustomerName("Frodo Baggins");
        payment.setCustomerTaxCode("BGGFRD68A01H501X");
        payment.setCustomerCountry("IT");
        payment.setInvoiceNumber("2026-0042");
        payment.setReceiptUrl("https://pay.example/receipts/1");
        payment.setRetainUntil(LocalDate.now().plusYears(10));
        database.save(payment);
        return payment;
    }

    private void saveThreePayments(AppUser owner) {
        savePayment(owner, "A", PaymentStatus.SUCCEEDED, 1000, THREE_DAYS_AGO);
        savePayment(owner, "B", PaymentStatus.SUCCEEDED, 5000, YESTERDAY);
        savePayment(owner, "C", PaymentStatus.PENDING, 3000, null);
    }

    private List<String> descriptions(MvcTestResult result) {
        return JsonPath.read(responseBody(result), "$.list[*].description");
    }

    private List<String> listDescriptions(String query) {
        return descriptions(mvc.get().uri("/me/payments" + query).with(USER).exchange());
    }

    @Test
    void findAllPayments_ownPayments_returnsThem() {
        saveThreePayments(createCurrentUser());

        assertThat(listDescriptions("")).containsExactlyInAnyOrder("A", "B", "C");
    }

    @Test
    void findAllPayments_paymentsOfOtherUser_returnsEmpty() {
        createCurrentUser();
        saveThreePayments(createOtherUser());

        assertThat(listDescriptions("")).isEmpty();
    }

    @Test
    void findAllPayments_ownAndOthersPayments_returnsOnlyOwn() {
        AppUser current = createCurrentUser();
        savePayment(current, "Mine", PaymentStatus.SUCCEEDED, 1000, YESTERDAY);
        savePayment(createOtherUser(), "Theirs", PaymentStatus.SUCCEEDED, 1000, YESTERDAY);

        assertThat(listDescriptions("")).containsExactly("Mine");
    }

    @Test
    void findAllPayments_paymentDetachedFromAccount_isNeverReturned() {
        createCurrentUser();
        savePayment(null, "Detached", PaymentStatus.SUCCEEDED, 1000, YESTERDAY);

        assertThat(listDescriptions("")).isEmpty();
    }

    @Test
    void findAllPayments_noSort_ordersByPaidAtDescendingWithUnpaidLast() {
        saveThreePayments(createCurrentUser());

        assertThat(listDescriptions("")).containsExactly("B", "A", "C");
    }

    @Test
    void findAllPayments_paidAtAscending_ordersOldestFirstWithUnpaidLast() {
        saveThreePayments(createCurrentUser());

        assertThat(listDescriptions("?sort=paidAt&descending=false")).containsExactly("A", "B", "C");
    }

    @Test
    void findAllPayments_amountAscending_ordersByAmount() {
        saveThreePayments(createCurrentUser());

        assertThat(listDescriptions("?sort=amount&descending=false")).containsExactly("A", "C", "B");
    }

    @Test
    void findAllPayments_amountDescending_ordersByAmountHighestFirst() {
        saveThreePayments(createCurrentUser());

        assertThat(listDescriptions("?sort=amount")).containsExactly("B", "C", "A");
    }

    @Test
    void findAllPayments_unknownSortKey_usesDefaultOrder() {
        saveThreePayments(createCurrentUser());

        assertThat(listDescriptions("?sort=retainUntil")).containsExactly("B", "A", "C");
    }

    @Test
    void findAllPayments_statusFilter_returnsOnlyThatStatus() {
        saveThreePayments(createCurrentUser());

        assertThat(listDescriptions("?status=SUCCEEDED")).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    void findAllPayments_statusFilterOfOtherUsersPayments_returnsEmpty() {
        createCurrentUser();
        saveThreePayments(createOtherUser());

        assertThat(listDescriptions("?status=SUCCEEDED")).isEmpty();
    }

    @Test
    void findAllPayments_unknownStatus_returnsBadRequest() {
        assertThat(mvc.get().uri("/me/payments?status=UNKNOWN").with(USER)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void findAllPayments_secondPageOfTwo_returnsRemainingPayment() {
        saveThreePayments(createCurrentUser());

        assertThat(listDescriptions("?size=2&page=2")).containsExactly("C");
    }

    @Test
    void findAllPayments_pagination_returnsTotals() {
        saveThreePayments(createCurrentUser());

        MvcTestResult result = mvc.get().uri("/me/payments?size=2").with(USER).exchange();

        assertThat(List.of(json(result, "$.totalRows"), json(result, "$.totalPages"), json(result, "$.page")))
                .containsExactly("3", "2", "1");
    }

    @Test
    void findAllPayments_adminRole_isAllowed() {
        assertThat(mvc.get().uri("/me/payments").with(ADMIN)).hasStatusOk();
    }

    @Test
    void getPayment_ownPayment_returnsDetail() {
        Payment payment = savePayment(createCurrentUser(), "Premium annual", PaymentStatus.SUCCEEDED, 2990, YESTERDAY);

        assertThat(mvc.get().uri("/me/payments/{id}", payment.getId()).with(USER)).bodyJson().isLenientlyEqualTo("""
                {"id":"%s","status":"SUCCEEDED","amountCents":2990,"currency":"EUR","description":"Premium annual",
                 "invoiceNumber":"2026-0042","receiptUrl":"https://pay.example/receipts/1","provider":"STRIPE",
                 "providerPaymentId":"%s","customerEmail":"frodo@shire.example","customerName":"Frodo Baggins",
                 "customerTaxCode":"BGGFRD68A01H501X","customerCountry":"IT"}
                """.formatted(payment.getId(), payment.getProviderPaymentId()));
    }

    @Test
    void getPayment_ownPayment_doesNotExposeRetentionDate() {
        Payment payment = savePayment(createCurrentUser(), "Premium annual", PaymentStatus.SUCCEEDED, 2990, YESTERDAY);

        assertThat(mvc.get().uri("/me/payments/{id}", payment.getId()).with(USER)).bodyJson().doesNotHavePath("$.retainUntil");
    }

    @Test
    void getPayment_paymentOfOtherUser_returnsNotFound() {
        createCurrentUser();
        Payment payment = savePayment(createOtherUser(), "Theirs", PaymentStatus.SUCCEEDED, 1000, YESTERDAY);

        assertThat(mvc.get().uri("/me/payments/{id}", payment.getId()).with(USER)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void getPayment_paymentOfOtherUser_returnsNotFoundMessage() {
        createCurrentUser();
        Payment payment = savePayment(createOtherUser(), "Theirs", PaymentStatus.SUCCEEDED, 1000, YESTERDAY);

        MvcTestResult result = mvc.get().uri("/me/payments/{id}", payment.getId()).with(USER).exchange();

        assertThat(json(result, "$.violations[0].message")).isEqualTo("Payment not found");
    }

    @Test
    void getPayment_unknownId_returnsNotFound() {
        createCurrentUser();

        assertThat(mvc.get().uri("/me/payments/{id}", UUID.randomUUID()).with(USER)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void getPayment_paymentDetachedFromAccount_returnsNotFound() {
        createCurrentUser();
        Payment payment = savePayment(null, "Detached", PaymentStatus.SUCCEEDED, 1000, YESTERDAY);

        assertThat(mvc.get().uri("/me/payments/{id}", payment.getId()).with(USER)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void getPayment_idNotUuid_returnsBadRequest() {
        assertThat(mvc.get().uri("/me/payments/not-a-uuid").with(USER)).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/me/payments", "/me/payments/" + ANY_PAYMENT})
    void paymentRoute_noToken_returnsUnauthorized(String path) {
        assertThat(mvc.get().uri(path)).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/me/payments", "/me/payments/" + ANY_PAYMENT})
    void paymentRoute_tokenWithoutRoles_returnsForbidden(String path) {
        assertThat(mvc.get().uri(path).with(NO_ROLES)).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void deleteAppUser_userWithPayments_returnsOk() {
        savePayment(createCurrentUser(), "Premium annual", PaymentStatus.SUCCEEDED, 2990, YESTERDAY);

        assertThat(mvc.delete().uri("/me").with(USER)).hasStatusOk();
    }

    @Test
    void deleteAppUser_userWithPayments_keepsPaymentsDetached() {
        savePayment(createCurrentUser(), "Premium annual", PaymentStatus.SUCCEEDED, 2990, YESTERDAY);

        mvc.delete().uri("/me").with(USER).exchange();

        assertThat(jdbc.queryForObject("select count(*) from payment where app_user_id is null", Integer.class)).isEqualTo(1);
    }

    @Test
    void deleteAppUser_userWithPayments_keepsAllPayments() {
        saveThreePayments(createCurrentUser());

        mvc.delete().uri("/me").with(USER).exchange();

        assertThat(countRows("payment")).isEqualTo(3);
    }

    @Test
    void deleteAppUser_userWithPayments_keepsCustomerData() {
        savePayment(createCurrentUser(), "Premium annual", PaymentStatus.SUCCEEDED, 2990, YESTERDAY);

        mvc.delete().uri("/me").with(USER).exchange();

        assertThat(jdbc.queryForList("select customer_email || '|' || customer_name || '|' || customer_tax_code || '|' || customer_country "
                + "|| '|' || invoice_number || '|' || amount_cents from payment", String.class))
                .containsExactly("frodo@shire.example|Frodo Baggins|BGGFRD68A01H501X|IT|2026-0042|2990");
    }

    @Test
    void deleteAppUser_userWithPayments_deletesTheUser() {
        savePayment(createCurrentUser(), "Premium annual", PaymentStatus.SUCCEEDED, 2990, YESTERDAY);

        mvc.delete().uri("/me").with(USER).exchange();

        assertThat(countRows("app_user")).isZero();
    }

    @Test
    void deleteAppUser_otherUserWithPayments_keepsPaymentAttachedToOtherUser() {
        createCurrentUser();
        savePayment(createOtherUser(), "Theirs", PaymentStatus.SUCCEEDED, 1000, YESTERDAY);

        mvc.delete().uri("/me").with(USER).exchange();

        assertThat(jdbc.queryForObject("select count(*) from payment where app_user_id is not null", Integer.class)).isEqualTo(1);
    }

    @Test
    void findAllPayments_accountDeletedAndCreatedAgain_doesNotReturnOldPayments() {
        savePayment(createCurrentUser(), "Old account", PaymentStatus.SUCCEEDED, 2990, YESTERDAY);
        mvc.delete().uri("/me").with(USER).exchange();
        createCurrentUser();

        assertThat(listDescriptions("")).isEmpty();
    }
}
