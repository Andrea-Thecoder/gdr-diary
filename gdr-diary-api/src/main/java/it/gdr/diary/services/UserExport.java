package it.gdr.diary.services;

import it.gdr.diary.model.Document;
import it.gdr.diary.model.GameCharacterKind;
import it.gdr.diary.model.PaymentProvider;
import it.gdr.diary.model.PaymentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UserExport(String fileName, Data data, List<File> files) {

    public record File(String path, Document document) {
    }

    public record Data(String format, int formatVersion, Instant exportedAt, Account account,
                       List<Character> characters, List<Payment> payments) {
    }

    public record Account(String email, boolean premium, Map<String, Object> settings, String avatarFile) {
    }

    public record Character(String name, GameCharacterKind kind, String campaignName, String backgroundColor,
                            String avatarFile, Map<String, Object> background, List<Sheet> sheets, List<Diary> diaries) {
    }

    public record Sheet(String title, String gameSystem, String originalFileName, String file) {
    }

    public record Diary(String title, String description, String coverColor, String font, Short fontSize,
                        String coverFile, List<Page> pages) {
    }

    public record Page(int pageNumber, Map<String, Object> content) {
    }

    public record Payment(PaymentProvider provider, String providerPaymentId, PaymentStatus status, long amountCents,
                          String currency, String description, Instant paidAt, String customerEmail, String customerName,
                          String customerTaxCode, String customerCountry, String invoiceNumber, String receiptUrl) {
    }
}
