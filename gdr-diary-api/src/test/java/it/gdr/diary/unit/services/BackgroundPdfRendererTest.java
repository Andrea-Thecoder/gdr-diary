package it.gdr.diary.unit.services;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.services.BackgroundPdfRenderer;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class BackgroundPdfRendererTest {

    private final BackgroundPdfRenderer renderer = new BackgroundPdfRenderer();

    private static Map<String, Object> text(String value, Map<String, Object>... marks) {
        return marks.length == 0
                ? Map.of("type", "text", "text", value)
                : Map.of("type", "text", "text", value, "marks", List.of(marks));
    }

    private static Map<String, Object> paragraph(Map<String, Object>... children) {
        return Map.of("type", "paragraph", "content", List.of(children));
    }

    private static Map<String, Object> doc(Map<String, Object>... blocks) {
        return Map.of("type", "doc", "content", List.of(blocks));
    }

    private static Map<String, Object> item(String value) {
        return Map.of("type", "listItem", "content", List.of(paragraph(text(value))));
    }

    private static PDDocument read(byte[] pdf) throws IOException {
        return Loader.loadPDF(pdf);
    }

    private static String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = read(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static int pagesOf(byte[] pdf) throws IOException {
        try (PDDocument document = read(pdf)) {
            return document.getNumberOfPages();
        }
    }

    @Test
    void render_paragraph_containsTheText() throws IOException {
        byte[] pdf = renderer.render("Il Background di Alira", null, doc(paragraph(text("Nata tra i salici."))));

        assertThat(textOf(pdf)).contains("Nata tra i salici.");
    }

    @Test
    void render_titleAndCampaign_areAtTheTop() throws IOException {
        byte[] pdf = renderer.render("Il Background di Alira", "Il Nord Spezzato", doc(paragraph(text("Testo"))));

        assertThat(textOf(pdf)).startsWith("Il Background di Alira\nIl Nord Spezzato\n");
    }

    @Test
    void render_italianLetters_keepsThem() throws IOException {
        byte[] pdf = renderer.render("Storia", null, doc(paragraph(text("Perché è già lì, città d’oro “nuova”"))));

        assertThat(textOf(pdf)).contains("Perché è già lì, città d’oro “nuova”");
    }

    @Test
    void render_characterTheFontCannotDraw_isReplacedByQuestionMark() throws IOException {
        byte[] pdf = renderer.render("Storia", null, doc(paragraph(text("Drago 🐉 rosso"))));

        assertThat(textOf(pdf)).contains("Drago ? rosso");
    }

    @Test
    void render_longStory_usesSeveralPages() throws IOException {
        byte[] pdf = renderer.render("Storia", null, doc(paragraph(text("parola ".repeat(7_000)))));

        assertThat(pagesOf(pdf)).isGreaterThan(5);
    }

    @Test
    void render_wordLongerThanTheLine_isSplitAcrossLines() throws IOException {
        byte[] pdf = renderer.render("Testo", null, doc(paragraph(text("a".repeat(50_000)))));

        assertThat(textOf(pdf).replaceAll("[^a]", "")).hasSize(50_000);
    }

    @Test
    void render_severalPages_numbersEveryPage() throws IOException {
        byte[] pdf = renderer.render("Storia", null, doc(paragraph(text("parola ".repeat(7_000)))));

        assertThat(textOf(pdf)).contains("1 / " + pagesOf(pdf)).contains(pagesOf(pdf) + " / " + pagesOf(pdf));
    }

    @Test
    void render_bulletList_drawsABulletForEachItem() throws IOException {
        byte[] pdf = renderer.render("Storia", null,
                doc(Map.of("type", "bulletList", "content", List.of(item("Spada"), item("Scudo")))));

        assertThat(textOf(pdf)).contains("• Spada").contains("• Scudo");
    }

    @Test
    void render_orderedList_numbersTheItemsFromTheStart() throws IOException {
        byte[] pdf = renderer.render("Storia", null, doc(Map.of("type", "orderedList",
                "attrs", Map.of("start", 3), "content", List.of(item("Terzo"), item("Quarto")))));

        assertThat(textOf(pdf)).contains("3. Terzo").contains("4. Quarto");
    }

    @Test
    void render_romanOrderedList_usesRomanNumbers() throws IOException {
        byte[] pdf = renderer.render("Storia", null, doc(Map.of("type", "orderedList",
                "attrs", Map.of("type", "I"), "content", List.of(item("Primo"), item("Secondo"), item("Terzo"), item("Quarto")))));

        assertThat(textOf(pdf)).contains("IV. Quarto");
    }

    @Test
    void render_letterOrderedList_usesLetters() throws IOException {
        byte[] pdf = renderer.render("Storia", null, doc(Map.of("type", "orderedList",
                "attrs", Map.of("type", "a"), "content", List.of(item("Primo"), item("Secondo")))));

        assertThat(textOf(pdf)).contains("b. Secondo");
    }

    @Test
    void render_everyMarkAndBlock_isDrawn() throws IOException {
        Map<String, Object> content = doc(
                paragraph(text("Capitolo", Map.of("type", "title", "attrs", Map.of("level", 1)))),
                Map.of("type", "paragraph", "attrs", Map.of("textAlign", "justify"), "content", List.of(
                        text("forte ", Map.of("type", "bold")),
                        text("obliquo ", Map.of("type", "italic"), Map.of("type", "underline")),
                        text("rosso", Map.of("type", "textColor", "attrs", Map.of("color", "red"))),
                        Map.of("type", "hardBreak"),
                        text("dopo l'a capo"))),
                Map.of("type", "blockquote", "content", List.of(paragraph(text("Una citazione")))),
                Map.of("type", "horizontalRule"),
                Map.of("type", "paragraph", "attrs", Map.of("textAlign", "center"), "content", List.of(text("Fine"))),
                Map.of("type", "paragraph"));

        byte[] pdf = renderer.render("Storia", null, content);

        assertThat(textOf(pdf)).contains("Capitolo", "forte obliquo rosso", "dopo l'a capo", "Una citazione", "Fine");
    }

    @Test
    void render_anyStory_isAValidPdf() {
        byte[] pdf = renderer.render("Storia", null, doc(paragraph(text("Testo"))));

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
