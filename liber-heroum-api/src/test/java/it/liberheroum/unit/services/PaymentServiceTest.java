package it.liberheroum.unit.services;

import static it.liberheroum.unit.support.QueryStub.pagedList;
import static it.liberheroum.unit.support.QueryStub.stubFind;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.ebean.Database;
import io.ebean.PagedList;
import it.liberheroum.dto.PagedResultDTO;
import it.liberheroum.dto.payment.BasePaymentDTO;
import it.liberheroum.dto.search.PaymentSearchRequest;
import it.liberheroum.model.Payment;
import it.liberheroum.model.PaymentProvider;
import it.liberheroum.model.PaymentStatus;
import it.liberheroum.security.JwtInspector;
import it.liberheroum.services.PaymentService;
import it.liberheroum.unit.support.QueryStub;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    void findAllPayments_payments_returnsMappedSummariesOnFirstPage() {
        QueryStub<Payment> stub = stubFind(database, Payment.class);
        PagedList<Payment> found = pagedList(List.of(payment()), 0, 20, 1);
        when(stub.where().findPagedList()).thenReturn(found);

        PagedResultDTO<BasePaymentDTO> result = service.findAllPayments(new PaymentSearchRequest());

        assertThat(result).satisfies(
                paged -> assertThat(paged.getList()).containsExactly(new BasePaymentDTO(PAYMENT_ID, PaymentStatus.SUCCEEDED, 2990,
                        "EUR", "Premium annual", PAID_AT, "2026-0042", "https://pay.example/receipts/1")),
                paged -> assertThat(paged.getPage()).isEqualTo(1));
    }
}
