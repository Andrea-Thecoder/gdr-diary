package it.gdr.diary.dto.payment;

import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.Payment;
import it.gdr.diary.model.PaymentStatus;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Summary of a payment of the logged-in user, used in lists")
public record BasePaymentDTO(
        @Schema(description = "Payment identifier", examples = {"9a3c5e71-4d2b-4f68-8b0e-6c1d7a2f3e45"})
        UUID id,

        @Schema(description = "Status of the payment", examples = {"SUCCEEDED"})
        PaymentStatus status,

        @Schema(description = "Amount in the smallest unit of the currency (cents)", examples = {"2990"})
        long amountCents,

        @Schema(description = "Currency, ISO 4217 code", examples = {"EUR"})
        String currency,

        @Schema(description = "What the payment is for", examples = {"Premium annual"})
        String description,

        @Schema(description = "When the payment succeeded, null until it does", examples = {"2026-11-10T09:15:00Z"}, nullable = true)
        Instant paidAt,

        @Schema(description = "Invoice number, null if there is none", examples = {"2026-0042"}, nullable = true)
        String invoiceNumber,

        @Schema(description = "Address of the receipt or invoice hosted by the payment provider, null if there is none", examples = {"https://pay.example/receipts/rcpt_1Nx"}, nullable = true)
        String receiptUrl) {

    public static BasePaymentDTO from(Payment payment) {
        return new BasePaymentDTO(
                payment.getId(),
                payment.getStatus(),
                payment.getAmountCents(),
                payment.getCurrency(),
                payment.getDescription(),
                payment.getPaidAt(),
                payment.getInvoiceNumber(),
                payment.getReceiptUrl());
    }
}
