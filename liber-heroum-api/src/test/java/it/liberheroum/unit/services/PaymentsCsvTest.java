package it.liberheroum.unit.services;

import static org.assertj.core.api.Assertions.assertThat;

import it.liberheroum.model.Payment;
import it.liberheroum.model.PaymentProvider;
import it.liberheroum.model.PaymentStatus;
import it.liberheroum.services.PaymentsCsv;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentsCsvTest {

    private static final String HEADER = "Data;Descrizione;Importo;Valuta;Stato;Numero fattura;Ricevuta;Nome;Codice fiscale;"
            + "Paese;Email;Gestore del pagamento;Codice del pagamento\r\n";

    private static Payment payment(String description, Instant paidAt, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setProvider(PaymentProvider.STRIPE);
        payment.setProviderPaymentId("pi_1");
        payment.setStatus(status);
        payment.setAmountCents(2990);
        payment.setCurrency("EUR");
        payment.setDescription(description);
        payment.setPaidAt(paidAt);
        payment.setCustomerEmail("alira@example.com");
        payment.setCustomerName("Alira Salici");
        payment.setCustomerTaxCode("SLCLRA90A41H501X");
        payment.setCustomerCountry("IT");
        payment.setInvoiceNumber("2026-0042");
        payment.setReceiptUrl("https://pay.example/r/1");
        return payment;
    }

    @Test
    void of_noPayments_returnsOnlyTheHeaderForSpreadsheets() {
        String csv = PaymentsCsv.of(List.of());

        assertThat(csv).isEqualTo("﻿" + HEADER);
    }

    @Test
    void of_payment_writesItalianDateAmountAndStatus() {
        String csv = PaymentsCsv.of(List.of(payment("Premium annuale", Instant.parse("2026-07-01T10:30:00Z"), PaymentStatus.SUCCEEDED)));

        assertThat(csv).endsWith(HEADER + "01/07/2026 12:30;Premium annuale;29,90;EUR;Pagato;2026-0042;https://pay.example/r/1;"
                + "Alira Salici;SLCLRA90A41H501X;IT;alira@example.com;STRIPE;pi_1\r\n");
    }

    @Test
    void of_pendingPaymentWithSpecialCharacters_leavesDateEmptyAndQuotesTheValue() {
        String csv = PaymentsCsv.of(List.of(payment("Piano \"Eroe\"; mensile", null, PaymentStatus.PENDING)));

        assertThat(csv).contains("\r\n;\"Piano \"\"Eroe\"\"; mensile\";29,90;EUR;In attesa;");
    }
}
