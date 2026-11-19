package it.liberheroum.services;

import it.liberheroum.model.Document;
import it.liberheroum.model.GameCharacterKind;
import it.liberheroum.model.PaymentProvider;
import it.liberheroum.model.PaymentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public record UserExport(String fileName, List<Entry> entries) {

    public sealed interface Entry permits StoredFile, GeneratedFile {
        String path();
    }

    public record StoredFile(String path, Document document) implements Entry {
    }

    public record GeneratedFile(String path, Supplier<byte[]> content) implements Entry {
    }

    public record Data(String format, int formatVersion, Instant exportedAt, Account account,
                       List<Character> characters, List<Payment> payments) {
    }

    public record Account(String email) {
    }

    public record Character(String name, GameCharacterKind kind, String campaignName, String backgroundColor,
                            String avatarFile, String storyFile, Map<String, Object> background,
                            List<Sheet> sheets, List<Diary> diaries) {
    }

    public record Sheet(String title, String gameSystem, String originalFileName, String file) {
    }

    public record Diary(String title, String description, String coverColor, String font, Short fontSize,
                        String coverFile, String pdfFile, List<Page> pages) {
    }

    public record Page(int pageNumber, Map<String, Object> content) {
    }

    public record Payment(PaymentProvider provider, String providerPaymentId, PaymentStatus status, long amountCents,
                          String currency, String description, Instant paidAt, String customerEmail, String customerName,
                          String customerTaxCode, String customerCountry, String invoiceNumber, String receiptUrl) {
    }
}
