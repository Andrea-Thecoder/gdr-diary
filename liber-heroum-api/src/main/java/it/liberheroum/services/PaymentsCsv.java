package it.liberheroum.services;

import it.liberheroum.model.Payment;
import it.liberheroum.model.PaymentStatus;
import org.apache.commons.lang3.StringUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class PaymentsCsv {

    private static final ZoneId ZONE = ZoneId.of("Europe/Rome");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String SEPARATOR = ";";
    private static final String LINE_END = "\r\n";
    private static final String BYTE_ORDER_MARK = "\uFEFF";
    private static final List<String> COLUMNS = List.of("Data", "Descrizione", "Importo", "Valuta", "Stato",
            "Numero fattura", "Ricevuta", "Nome", "Codice fiscale", "Paese", "Email", "Gestore del pagamento",
            "Codice del pagamento");
    private static final Map<PaymentStatus, String> STATUS_LABELS = Map.of(
            PaymentStatus.PENDING, "In attesa",
            PaymentStatus.SUCCEEDED, "Pagato",
            PaymentStatus.FAILED, "Non riuscito",
            PaymentStatus.REFUNDED, "Rimborsato");

    private PaymentsCsv() {
    }

    public static String of(List<Payment> payments) {
        StringBuilder csv = new StringBuilder(BYTE_ORDER_MARK).append(line(COLUMNS));
        payments.forEach(payment -> csv.append(line(List.of(
                payment.getPaidAt() == null ? "" : DATE_TIME.format(payment.getPaidAt().atZone(ZONE)),
                StringUtils.defaultString(payment.getDescription()),
                String.format(Locale.ITALY, "%.2f", payment.getAmountCents() / 100.0),
                StringUtils.defaultString(payment.getCurrency()),
                STATUS_LABELS.getOrDefault(payment.getStatus(), ""),
                StringUtils.defaultString(payment.getInvoiceNumber()),
                StringUtils.defaultString(payment.getReceiptUrl()),
                StringUtils.defaultString(payment.getCustomerName()),
                StringUtils.defaultString(payment.getCustomerTaxCode()),
                StringUtils.defaultString(payment.getCustomerCountry()),
                StringUtils.defaultString(payment.getCustomerEmail()),
                payment.getProvider() == null ? "" : payment.getProvider().name(),
                StringUtils.defaultString(payment.getProviderPaymentId())))));
        return csv.toString();
    }

    private static String line(List<String> values) {
        return values.stream().map(PaymentsCsv::value).collect(Collectors.joining(SEPARATOR)) + LINE_END;
    }

    private static String value(String value) {
        if (StringUtils.containsAny(value, SEPARATOR, "\"", "\n", "\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
