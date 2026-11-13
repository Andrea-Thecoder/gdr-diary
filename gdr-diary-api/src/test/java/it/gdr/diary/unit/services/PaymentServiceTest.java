package it.gdr.diary.unit.services;

import static it.gdr.diary.unit.support.QueryStub.pagedList;
import static it.gdr.diary.unit.support.QueryStub.stubFind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.ebean.Database;
import io.ebean.PagedList;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.payment.BasePaymentDTO;
import it.gdr.diary.dto.payment.DetailPaymentDTO;
import it.gdr.diary.dto.search.PaymentSearchRequest;
import it.gdr.diary.model.Payment;
import it.gdr.diary.model.PaymentProvider;
import it.gdr.diary.model.PaymentStatus;
import it.gdr.diary.security.JwtInspector;
import it.gdr.diary.services.PaymentService;
import it.gdr.diary.unit.support.QueryStub;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final String SUBJECT = "6f1c2a8e-3b4d-4e5f-9a0b-1c2d3e4f5a6b";
    private static final UUID PAYMENT_ID = UUID.fromString("9a3c5e71-4d2b-4f68-8b0e-6c1d7a2f3e45");
    private static final Instant PAID_AT = Instant.parse("2026-11-10T09:15:00Z");

    @Mock
    private Database database;
    @Mock
    private JwtInspector jwtInspector;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(database, jwtInspector);
        when(jwtInspector.getSubject()).thenReturn(SUBJECT);
    }

    private static Payment payment() {
        Payment payment = new Payment();
        payment.setId(PAYMENT_ID);
        payment.setProvider(PaymentProvider.STRIPE);
        payment.setProviderPaymentId("pi_123");
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setAmountCents(2990);
        payment.setCurrency("EUR");
        payment.setDescription("Premium annual");
        payment.setPaidAt(PAID_AT);
        payment.setCustomerEmail("alira@example.com");
        payment.setCustomerName("Alira Stormwind");
        payment.setCustomerTaxCode("RSSMRA80A01H501U");
        payment.setCustomerCountry("IT");
        payment.setInvoiceNumber("2026-0042");
        payment.setReceiptUrl("https://pay.example/receipts/1");
        return payment;
    }

    private QueryStub<Payment> givenPayments(List<Payment> payments) {
        QueryStub<Payment> stub = stubFind(database, Payment.class);
        PagedList<Payment> found = pagedList(payments, 0, 20, payments.size());
        when(stub.where().findPagedList()).thenReturn(found);
        return stub;
    }

    @Test
    void findAllPayments_anyRequest_filtersByOwnerOfToken() {
        QueryStub<Payment> payments = givenPayments(List.of());

        service.findAllPayments(new PaymentSearchRequest());

        verify(payments.where()).eq("appUser.keycloakId", SUBJECT);
    }

    @Test
    void findAllPayments_noSort_ordersByPaidAtDescendingNullsLast() {
        QueryStub<Payment> payments = givenPayments(List.of());

        service.findAllPayments(new PaymentSearchRequest());

        verify(payments.where()).orderBy("paidAt desc nulls last, id asc");
    }

    @Test
    void findAllPayments_statusFilter_appliesFilterOfRequest() {
        QueryStub<Payment> payments = givenPayments(List.of());
        PaymentSearchRequest request = new PaymentSearchRequest();
        request.setStatus(PaymentStatus.FAILED);

        service.findAllPayments(request);

        verify(payments.where()).eq("status", PaymentStatus.FAILED);
    }

    @Test
    void findAllPayments_payments_mapsSummaryFields() {
        givenPayments(List.of(payment()));

        PagedResultDTO<BasePaymentDTO> result = service.findAllPayments(new PaymentSearchRequest());

        assertThat(result.getList()).containsExactly(new BasePaymentDTO(PAYMENT_ID, PaymentStatus.SUCCEEDED, 2990, "EUR",
                "Premium annual", PAID_AT, "2026-0042", "https://pay.example/receipts/1"));
    }

    @Test
    void findAllPayments_firstPage_returnsOneBasedPage() {
        givenPayments(List.of(payment()));

        PagedResultDTO<BasePaymentDTO> result = service.findAllPayments(new PaymentSearchRequest());

        assertThat(result.getPage()).isEqualTo(1);
    }

    @Test
    void getPaymentById_ownPayment_filtersByIdAndOwnerOfToken() {
        QueryStub<Payment> payments = stubFind(database, Payment.class);
        when(payments.where().findOne()).thenReturn(payment());

        service.getPaymentById(PAYMENT_ID);

        verify(payments.where()).idEq(PAYMENT_ID);
        verify(payments.where()).eq("appUser.keycloakId", SUBJECT);
    }

    @Test
    void getPaymentById_ownPayment_mapsDetailFields() {
        QueryStub<Payment> payments = stubFind(database, Payment.class);
        when(payments.where().findOne()).thenReturn(payment());

        DetailPaymentDTO result = service.getPaymentById(PAYMENT_ID);

        assertThat(result).isEqualTo(new DetailPaymentDTO(PAYMENT_ID, PaymentStatus.SUCCEEDED, 2990, "EUR", "Premium annual",
                PAID_AT, "2026-0042", "https://pay.example/receipts/1", PaymentProvider.STRIPE, "pi_123", "alira@example.com",
                "Alira Stormwind", "RSSMRA80A01H501U", "IT"));
    }

    @Test
    void getPaymentById_notOwnedOrMissing_throwsNotFound() {
        QueryStub<Payment> payments = stubFind(database, Payment.class);
        when(payments.where().findOne()).thenReturn(null);

        assertThatThrownBy(() -> service.getPaymentById(PAYMENT_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(e.getReason()).isEqualTo("Payment not found");
                });
    }
}
