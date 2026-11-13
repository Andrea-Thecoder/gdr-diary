package it.gdr.diary.unit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import it.gdr.diary.controller.PaymentController;
import it.gdr.diary.dto.PagedResultDTO;
import it.gdr.diary.dto.payment.BasePaymentDTO;
import it.gdr.diary.dto.payment.DetailPaymentDTO;
import it.gdr.diary.dto.search.PaymentSearchRequest;
import it.gdr.diary.exception.ExceptionMapper;
import it.gdr.diary.model.PaymentProvider;
import it.gdr.diary.model.PaymentStatus;
import it.gdr.diary.services.PaymentService;
import java.time.Instant;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    private static final UUID PAYMENT_ID = UUID.fromString("9a3c5e71-4d2b-4f68-8b0e-6c1d7a2f3e45");
    private static final Instant PAID_AT = Instant.parse("2026-11-10T09:15:00Z");

    @Mock
    private PaymentService service;

    private MockMvcTester mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcTester.create(MockMvcBuilders.standaloneSetup(new PaymentController(service))
                .setControllerAdvice(new ExceptionMapper())
                .build());
    }

    private static BasePaymentDTO base() {
        return new BasePaymentDTO(PAYMENT_ID, PaymentStatus.SUCCEEDED, 2990, "EUR", "Premium annual", PAID_AT, "2026-0042",
                "https://pay.example/receipts/1");
    }

    private static DetailPaymentDTO detail() {
        return new DetailPaymentDTO(PAYMENT_ID, PaymentStatus.SUCCEEDED, 2990, "EUR", "Premium annual", PAID_AT, "2026-0042",
                "https://pay.example/receipts/1", PaymentProvider.STRIPE, "pi_123", "alira@example.com", "Alira Stormwind",
                "RSSMRA80A01H501U", "IT");
    }

    @Test
    void findAllPayments_noParameters_returnsOk() {
        when(service.findAllPayments(any())).thenReturn(PagedResultDTO.of(List.of(), 0, 1, 20));

        assertThat(mvc.get().uri("/me/payments")).hasStatusOk();
    }

    @Test
    void findAllPayments_paymentsFound_returnsPagedList() {
        when(service.findAllPayments(any())).thenReturn(PagedResultDTO.of(List.of(base()), 1, 1, 20));

        assertThat(mvc.get().uri("/me/payments")).bodyJson().extractingPath("$.list[0].amountCents").isEqualTo(2990);
    }

    @Test
    void findAllPayments_paymentsFound_doesNotExposeRetentionOrAudit() {
        when(service.findAllPayments(any())).thenReturn(PagedResultDTO.of(List.of(base()), 1, 1, 20));

        assertThat(mvc.get().uri("/me/payments")).bodyText().doesNotContain("retainUntil", "createdBy", "version");
    }

    @Test
    void findAllPayments_queryParameters_bindsSearchRequest() {
        when(service.findAllPayments(any())).thenReturn(PagedResultDTO.of(List.of(), 0, 2, 5));
        ArgumentCaptor<PaymentSearchRequest> request = ArgumentCaptor.forClass(PaymentSearchRequest.class);

        mvc.get().uri("/me/payments?status=FAILED&page=2&size=5&sort=amount&descending=false").exchange();

        verify(service).findAllPayments(request.capture());
        assertThat(request.getValue())
                .extracting(PaymentSearchRequest::getStatus, PaymentSearchRequest::getPage, PaymentSearchRequest::getSize,
                        PaymentSearchRequest::getSort, PaymentSearchRequest::isDescending)
                .containsExactly(PaymentStatus.FAILED, 2, 5, "amount", false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=0", "size=0", "size=101", "status=UNKNOWN"})
    void findAllPayments_invalidParameters_returnsBadRequestWithoutCallingService(String query) {
        assertThat(mvc.get().uri("/me/payments?" + query)).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void getPaymentById_existingPayment_returnsDetail() {
        when(service.getPaymentById(PAYMENT_ID)).thenReturn(detail());

        assertThat(mvc.get().uri("/me/payments/{id}", PAYMENT_ID)).bodyJson()
                .extractingPath("$.providerPaymentId").isEqualTo("pi_123");
    }

    @Test
    void getPaymentById_existingPayment_doesNotExposeRetentionOrAudit() {
        when(service.getPaymentById(PAYMENT_ID)).thenReturn(detail());

        assertThat(mvc.get().uri("/me/payments/{id}", PAYMENT_ID)).bodyText().doesNotContain("retainUntil", "createdBy", "version");
    }

    @Test
    void getPaymentById_paymentNotFound_returnsNotFound() {
        when(service.getPaymentById(PAYMENT_ID)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));

        assertThat(mvc.get().uri("/me/payments/{id}", PAYMENT_ID)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void getPaymentById_idNotUuid_returnsBadRequest() {
        assertThat(mvc.get().uri("/me/payments/not-a-uuid")).hasStatus(HttpStatus.BAD_REQUEST);
    }
}
