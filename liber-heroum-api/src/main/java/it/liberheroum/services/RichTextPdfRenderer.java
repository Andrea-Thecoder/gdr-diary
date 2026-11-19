package it.liberheroum.services;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RichTextPdfRenderer {

    private static final PDRectangle PAGE = PDRectangle.A4;
    private static final float MARGIN = 64f;
    private static final float FOOTER = 28f;
    private static final float WIDTH = PAGE.getWidth() - 2 * MARGIN;
    private static final float BODY_SIZE = 12f;
    private static final float SUBTITLE_SIZE = 13f;
    private static final float TITLE_SIZE = 24f;
    private static final float FOOTER_SIZE = 9f;
    private static final float LINE_FACTOR = 1.45f;
    private static final float BASELINE_FACTOR = 1.1f;
    private static final float BLOCK_GAP = 6f;
    private static final float INDENT = 22f;
    private static final float MARKER_GAP = 6f;
    private static final float QUOTE_BAR_OFFSET = 12f;
    private static final float RULE_GAP = 10f;
    private static final Color INK = new Color(0x1F1A17);
    private static final Color SOFT = new Color(0x6B6259);
    private static final Color RULE = new Color(0xB8A98A);
    private static final Map<Integer, Float> TITLE_SIZES = Map.of(1, 18f, 2, 15f);
    private static final Map<String, Color> COLORS = Map.of(
            "red", new Color(0x9B1C1C),
            "orange", new Color(0xA64B00),
            "green", new Color(0x1F6B3A),
            "teal", new Color(0x0F6670),
            "blue", new Color(0x1F4F9E),
            "purple", new Color(0x6A2FA6),
            "magenta", new Color(0x9C2A6A),
            "brown", new Color(0x6B4423));
    private static final String FONT_FOLDER = "/fonts/LiberationSerif-";
    private static final String REPLACEMENT = "?";
    private static final String PAGE_LABEL = "Pagina ";
    private static final String NO_PAGES = "Questo diario non ha ancora pagine.";
    private static final float NOTE_SIZE = 11f;

    public byte[] render(String title, String subtitle, Map<String, Object> content) {
        return write(writer -> {
            writer.header(title, subtitle);
            writer.blocks(children(content), new Frame(0, List.of()));
        });
    }

    public byte[] renderPages(String title, String subtitle, List<Map<String, Object>> pages) {
        return write(writer -> {
            writer.header(title, subtitle);
            if (pages.isEmpty()) {
                writer.note(NO_PAGES);
            }
            for (int index = 0; index < pages.size(); index++) {
                if (index > 0) {
                    writer.pageBreak();
                }
                writer.note(PAGE_LABEL + (index + 1));
                writer.blocks(children(pages.get(index)), new Frame(0, List.of()));
            }
        });
    }

    private byte[] write(Content content) {
        try (PDDocument document = new PDDocument()) {
            Writer writer = new Writer(document, Fonts.load(document));
            content.writeTo(writer);
            writer.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write the PDF", e);
        }
    }

    @FunctionalInterface
    private interface Content {
        void writeTo(Writer writer) throws IOException;
    }

    private record Style(boolean bold, boolean italic, boolean underline, float size, Color color) {

        static Style body() {
            return new Style(false, false, false, BODY_SIZE, INK);
        }
    }

    private record Token(String text, Style style, float width, boolean space, boolean lineBreak) {
    }

    private record Line(List<Token> tokens, float width, boolean last) {

        float size() {
            return (float) tokens.stream().mapToDouble(token -> token.style().size()).max().orElse(BODY_SIZE);
        }

        long spaces() {
            return tokens.stream().filter(Token::space).count();
        }
    }

    private record Frame(float indent, List<Float> bars) {

        Frame inner(boolean quote) {
            List<Float> nextBars = new ArrayList<>(bars);
            if (quote) {
                nextBars.add(indent + QUOTE_BAR_OFFSET);
            }
            return new Frame(indent + INDENT, nextBars);
        }
    }

    private record Fonts(PDType0Font regular, PDType0Font bold, PDType0Font italic, PDType0Font boldItalic,
                         Map<PDType0Font, Map<Integer, Boolean>> glyphs) {

        static Fonts load(PDDocument document) throws IOException {
            return new Fonts(font(document, "Regular"), font(document, "Bold"), font(document, "Italic"),
                    font(document, "BoldItalic"), new HashMap<>());
        }

        private static PDType0Font font(PDDocument document, String style) throws IOException {
            try (InputStream input = RichTextPdfRenderer.class.getResourceAsStream(FONT_FOLDER + style + ".ttf")) {
                if (input == null) {
                    throw new IOException("Missing font " + style);
                }
                return PDType0Font.load(document, input, true);
            }
        }

        PDType0Font of(Style style) {
            if (style.bold() && style.italic()) {
                return boldItalic;
            }
            if (style.bold()) {
                return bold;
            }
            return style.italic() ? italic : regular;
        }

        String printable(String text, PDType0Font font) {
            StringBuilder result = new StringBuilder();
            text.codePoints().forEach(codePoint -> {
                String character = Character.isWhitespace(codePoint) ? " " : Character.toString(codePoint);
                result.append(hasGlyph(font, character) ? character : REPLACEMENT);
            });
            return result.toString();
        }

        private boolean hasGlyph(PDType0Font font, String character) {
            return glyphs.computeIfAbsent(font, key -> new HashMap<>()).computeIfAbsent(character.codePointAt(0), key -> {
                try {
                    font.encode(character);
                    return true;
                } catch (IOException | IllegalArgumentException e) {
                    return false;
                }
            });
        }

        float width(String text, Style style) {
            try {
                return of(style).getStringWidth(text) / 1000f * style.size();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static final class Writer {

        private final PDDocument document;
        private final Fonts fonts;
        private PDPageContentStream stream;
        private float y;

        Writer(PDDocument document, Fonts fonts) throws IOException {
            this.document = document;
            this.fonts = fonts;
            newPage();
        }

        void header(String title, String subtitle) throws IOException {
            Style titleStyle = new Style(true, false, false, TITLE_SIZE, INK);
            for (Line line : lines(List.of(token(title, titleStyle)), WIDTH)) {
                drawLine(line, new Frame(0, List.of()), "left", null);
            }
            if (subtitle != null) {
                Style subtitleStyle = new Style(false, true, false, SUBTITLE_SIZE, SOFT);
                for (Line line : lines(List.of(token(subtitle, subtitleStyle)), WIDTH)) {
                    drawLine(line, new Frame(0, List.of()), "left", null);
                }
            }
            rule(0);
            y -= BLOCK_GAP * 2;
        }

        void note(String text) throws IOException {
            Style noteStyle = new Style(false, true, false, NOTE_SIZE, SOFT);
            for (Line line : lines(List.of(token(text, noteStyle)), WIDTH)) {
                drawLine(line, new Frame(0, List.of()), "left", null);
            }
            y -= BLOCK_GAP;
        }

        void pageBreak() throws IOException {
            newPage();
        }

        void blocks(List<Map<String, Object>> nodes, Frame frame) throws IOException {
            for (Map<String, Object> node : nodes) {
                block(node, frame, null);
            }
        }

        private void block(Map<String, Object> node, Frame frame, String marker) throws IOException {
            switch (type(node)) {
                case "paragraph" -> paragraph(node, frame, marker);
                case "blockquote" -> blocks(children(node), frame.inner(true));
                case "bulletList" -> list(node, frame, false);
                case "orderedList" -> list(node, frame, true);
                case "horizontalRule" -> {
                    ensureSpace(RULE_GAP * 2);
                    y -= RULE_GAP;
                    rule(frame.indent());
                    y -= RULE_GAP;
                }
                default -> blocks(children(node), frame);
            }
        }

        private void list(Map<String, Object> node, Frame frame, boolean ordered) throws IOException {
            Map<String, Object> attrs = attrs(node);
            int number = attrs.get("start") instanceof Number start ? start.intValue() : 1;
            String kind = attrs.get("type") instanceof String value ? value : "1";
            Frame inner = frame.inner(false);
            for (Map<String, Object> item : children(node)) {
                String marker = ordered ? orderedMarker(number, kind) : "•";
                List<Map<String, Object>> itemBlocks = children(item);
                for (int index = 0; index < itemBlocks.size(); index++) {
                    block(itemBlocks.get(index), inner, index == 0 ? marker : null);
                }
                number++;
            }
        }

        private void paragraph(Map<String, Object> node, Frame frame, String marker) throws IOException {
            List<Token> tokens = new ArrayList<>();
            for (Map<String, Object> child : children(node)) {
                if ("hardBreak".equals(type(child))) {
                    tokens.add(new Token("", Style.body(), 0, false, true));
                } else if (child.get("text") instanceof String text) {
                    tokens.addAll(words(text, style(child)));
                }
            }
            String align = attrs(node).get("textAlign") instanceof String value ? value : "left";
            List<Line> lines = lines(tokens, WIDTH - frame.indent());
            for (int index = 0; index < lines.size(); index++) {
                drawLine(lines.get(index), frame, align, index == 0 ? marker : null);
            }
            y -= BLOCK_GAP;
        }

        private List<Token> words(String text, Style style) {
            List<Token> tokens = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            Boolean spaces = null;
            String printable = fonts.printable(text, fonts.of(style));
            for (int index = 0; index < printable.length(); index++) {
                char character = printable.charAt(index);
                boolean space = character == ' ';
                if (spaces != null && spaces != space) {
                    tokens.add(token(current.toString(), style));
                    current.setLength(0);
                }
                current.append(character);
                spaces = space;
            }
            if (!current.isEmpty()) {
                tokens.add(token(current.toString(), style));
            }
            return tokens;
        }

        private Token token(String text, Style style) {
            String printable = fonts.printable(text, fonts.of(style));
            return new Token(printable, style, fonts.width(printable, style), printable.isBlank(), false);
        }

        private List<Line> lines(List<Token> tokens, float available) {
            List<Line> lines = new ArrayList<>();
            List<Token> current = new ArrayList<>();
            float width = 0;
            for (Token token : tokens) {
                if (token.lineBreak()) {
                    lines.add(close(current, true));
                    current = new ArrayList<>();
                    width = 0;
                    continue;
                }
                if (token.space() && current.isEmpty()) {
                    continue;
                }
                if (!token.space() && width + token.width() > available && !current.isEmpty()) {
                    lines.add(close(current, false));
                    current = new ArrayList<>();
                    width = 0;
                }
                if (!token.space() && token.width() > available) {
                    for (Token piece : split(token, available)) {
                        if (width + piece.width() > available && !current.isEmpty()) {
                            lines.add(close(current, false));
                            current = new ArrayList<>();
                            width = 0;
                        }
                        current.add(piece);
                        width += piece.width();
                    }
                    continue;
                }
                current.add(token);
                width += token.width();
            }
            lines.add(close(current, true));
            return lines;
        }

        private List<Token> split(Token token, float available) {
            List<Token> pieces = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            token.text().codePoints().forEach(codePoint -> {
                String next = current + Character.toString(codePoint);
                if (fonts.width(next, token.style()) > available && !current.isEmpty()) {
                    pieces.add(token(current.toString(), token.style()));
                    current.setLength(0);
                }
                current.appendCodePoint(codePoint);
            });
            if (!current.isEmpty()) {
                pieces.add(token(current.toString(), token.style()));
            }
            return pieces;
        }

        private Line close(List<Token> tokens, boolean last) {
            List<Token> trimmed = new ArrayList<>(tokens);
            while (!trimmed.isEmpty() && trimmed.getLast().space()) {
                trimmed.removeLast();
            }
            float width = (float) trimmed.stream().mapToDouble(Token::width).sum();
            return new Line(trimmed, width, last);
        }

        private void drawLine(Line line, Frame frame, String align, String marker) throws IOException {
            float size = line.size();
            float height = size * LINE_FACTOR;
            ensureSpace(height);
            float top = y;
            float baseline = top - size * BASELINE_FACTOR;
            float left = MARGIN + frame.indent();
            float free = WIDTH - frame.indent() - line.width();
            float x = switch (align) {
                case "center" -> left + free / 2;
                case "right" -> left + free;
                default -> left;
            };
            float extraSpace = "justify".equals(align) && !line.last() && line.spaces() > 0 ? free / line.spaces() : 0;
            if (marker != null) {
                Style markerStyle = Style.body();
                Token markerToken = token(marker, markerStyle);
                text(markerToken, left - markerToken.width() - MARKER_GAP, baseline);
            }
            for (Token token : line.tokens()) {
                float width = token.width() + (token.space() ? extraSpace : 0);
                if (!token.space()) {
                    text(token, x, baseline);
                }
                if (token.style().underline()) {
                    underline(token.style(), x, baseline, width);
                }
                x += width;
            }
            for (Float bar : frame.bars()) {
                stream.setStrokingColor(RULE);
                stream.setLineWidth(1.5f);
                stream.moveTo(MARGIN + bar, top);
                stream.lineTo(MARGIN + bar, top - height);
                stream.stroke();
            }
            y = top - height;
        }

        private void text(Token token, float x, float baseline) throws IOException {
            stream.beginText();
            stream.setFont(fonts.of(token.style()), token.style().size());
            stream.setNonStrokingColor(token.style().color());
            stream.newLineAtOffset(x, baseline);
            stream.showText(token.text());
            stream.endText();
        }

        private void underline(Style style, float x, float baseline, float width) throws IOException {
            float offset = style.size() * 0.12f;
            stream.setStrokingColor(style.color());
            stream.setLineWidth(Math.max(0.5f, style.size() * 0.05f));
            stream.moveTo(x, baseline - offset);
            stream.lineTo(x + width, baseline - offset);
            stream.stroke();
        }

        private void rule(float indent) throws IOException {
            ensureSpace(BLOCK_GAP);
            stream.setStrokingColor(RULE);
            stream.setLineWidth(0.8f);
            stream.moveTo(MARGIN + indent, y);
            stream.lineTo(MARGIN + WIDTH, y);
            stream.stroke();
        }

        private void ensureSpace(float height) throws IOException {
            if (y - height < MARGIN + FOOTER) {
                newPage();
            }
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PAGE);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE.getHeight() - MARGIN;
        }

        void finish() throws IOException {
            stream.close();
            int total = document.getNumberOfPages();
            Style footerStyle = new Style(false, false, false, FOOTER_SIZE, SOFT);
            for (int index = 0; index < total; index++) {
                PDPage page = document.getPage(index);
                try (PDPageContentStream footer = new PDPageContentStream(document, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    String label = (index + 1) + " / " + total;
                    float width = fonts.width(label, footerStyle);
                    footer.beginText();
                    footer.setFont(fonts.of(footerStyle), FOOTER_SIZE);
                    footer.setNonStrokingColor(SOFT);
                    footer.newLineAtOffset((PAGE.getWidth() - width) / 2, MARGIN / 2);
                    footer.showText(label);
                    footer.endText();
                }
            }
        }

        private Style style(Map<String, Object> textNode) {
            boolean bold = false;
            boolean italic = false;
            boolean underline = false;
            float size = BODY_SIZE;
            Color color = INK;
            for (Map<String, Object> mark : maps(textNode.get("marks"))) {
                switch (type(mark)) {
                    case "bold" -> bold = true;
                    case "italic" -> italic = true;
                    case "underline" -> underline = true;
                    case "title" -> {
                        bold = true;
                        int level = attrs(mark).get("level") instanceof Number value ? value.intValue() : 1;
                        size = TITLE_SIZES.getOrDefault(level, TITLE_SIZES.get(1));
                    }
                    case "textColor" -> color = COLORS.getOrDefault(String.valueOf(attrs(mark).get("color")), INK);
                    default -> {
                    }
                }
            }
            return new Style(bold, italic, underline, size, color);
        }
    }

    static String orderedMarker(int number, String kind) {
        String label = switch (kind) {
            case "a" -> letters(number).toLowerCase(Locale.ROOT);
            case "A" -> letters(number);
            case "i" -> roman(number).toLowerCase(Locale.ROOT);
            case "I" -> roman(number);
            default -> String.valueOf(number);
        };
        return label + ".";
    }

    private static String letters(int number) {
        if (number < 1) {
            return String.valueOf(number);
        }
        StringBuilder result = new StringBuilder();
        int rest = number;
        while (rest > 0) {
            rest--;
            result.insert(0, (char) ('A' + rest % 26));
            rest /= 26;
        }
        return result.toString();
    }

    private static String roman(int number) {
        if (number < 1 || number > 3999) {
            return String.valueOf(number);
        }
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder result = new StringBuilder();
        int rest = number;
        for (int index = 0; index < values.length; index++) {
            while (rest >= values[index]) {
                result.append(symbols[index]);
                rest -= values[index];
            }
        }
        return result.toString();
    }

    private static String type(Map<String, Object> node) {
        return node.get("type") instanceof String value ? value : "";
    }

    private static Map<String, Object> attrs(Map<String, Object> node) {
        return node.get("attrs") instanceof Map<?, ?> attrs ? cast(attrs) : Map.of();
    }

    private static List<Map<String, Object>> children(Map<String, Object> node) {
        return maps(node.get("content"));
    }

    private static List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> map) {
                result.add(cast(map));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }
}
