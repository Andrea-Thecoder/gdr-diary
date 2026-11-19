package it.liberheroum.services;

import it.liberheroum.model.CharacterBackground;

import java.util.List;
import java.util.Map;

public record BackgroundText(int length, String preview) {

    private static final String TEXT = "text";
    private static final String HARD_BREAK = "hardBreak";
    private static final String HORIZONTAL_RULE = "horizontalRule";

    public static BackgroundText of(Map<String, Object> document) {
        Collector collector = new Collector();
        collector.visit(document);
        return new BackgroundText(collector.length, preview(collector.flow.toString()));
    }

    private static String preview(String flow) {
        String compact = flow.replaceAll("\\s+", " ").trim();
        if (compact.codePointCount(0, compact.length()) <= CharacterBackground.PREVIEW_LENGTH) {
            return compact;
        }
        return compact.substring(0, compact.offsetByCodePoints(0, CharacterBackground.PREVIEW_LENGTH)).trim();
    }

    private static final class Collector {

        private final StringBuilder flow = new StringBuilder();
        private int length;

        private void visit(Object value) {
            if (!(value instanceof Map<?, ?> node)) {
                return;
            }
            Object type = node.get("type");
            if (TEXT.equals(type) && node.get(TEXT) instanceof String text) {
                length += text.codePointCount(0, text.length());
                flow.append(text);
                return;
            }
            if (HARD_BREAK.equals(type) || HORIZONTAL_RULE.equals(type)) {
                flow.append(' ');
                return;
            }
            if (node.get("content") instanceof List<?> children) {
                children.forEach(this::visit);
                flow.append(' ');
            }
        }
    }
}
