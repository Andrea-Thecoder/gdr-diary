package it.gdr.diary.services;

import org.apache.commons.lang3.StringUtils;

import java.text.Normalizer;
import java.util.Locale;

public final class FileNames {

    private FileNames() {
    }

    public static String slug(String value, int maxLength, String fallback) {
        String plain = Normalizer.normalize(StringUtils.defaultString(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        String shortened = StringUtils.left(plain, maxLength).replaceAll("-+$", "");
        return shortened.isEmpty() ? fallback : shortened;
    }
}
