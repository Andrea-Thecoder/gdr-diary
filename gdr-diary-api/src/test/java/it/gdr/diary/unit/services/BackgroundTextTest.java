package it.gdr.diary.unit.services;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.services.BackgroundText;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BackgroundTextTest {

    private static Map<String, Object> text(String value) {
        return Map.of("type", "text", "text", value);
    }

    private static Map<String, Object> paragraph(String... values) {
        return Map.of("type", "paragraph", "content", java.util.Arrays.stream(values).map(BackgroundTextTest::text).toList());
    }

    private static Map<String, Object> doc(Object... blocks) {
        return Map.of("type", "doc", "content", List.of(blocks));
    }

    @Test
    void of_emptyParagraph_hasNoTextAndNoPreview() {
        BackgroundText result = BackgroundText.of(doc(Map.of("type", "paragraph")));

        assertThat(result).isEqualTo(new BackgroundText(0, ""));
    }

    @Test
    void of_singleParagraph_countsItsCharactersSpacesIncluded() {
        BackgroundText result = BackgroundText.of(doc(paragraph("Born in Erebor")));

        assertThat(result.length()).isEqualTo(14);
    }

    @Test
    void of_singleParagraph_previewIsItsText() {
        BackgroundText result = BackgroundText.of(doc(paragraph("Born in Erebor")));

        assertThat(result.preview()).isEqualTo("Born in Erebor");
    }

    @Test
    void of_twoParagraphs_countsOnlyTextNotTheBlockSeparators() {
        BackgroundText result = BackgroundText.of(doc(paragraph("One"), paragraph("Two")));

        assertThat(result.length()).isEqualTo(6);
    }

    @Test
    void of_twoParagraphs_previewSeparatesThemWithASpace() {
        BackgroundText result = BackgroundText.of(doc(paragraph("One"), paragraph("Two")));

        assertThat(result.preview()).isEqualTo("One Two");
    }

    @Test
    void of_textSplitInMarkedRuns_joinsTheRunsWithoutSeparator() {
        BackgroundText result = BackgroundText.of(doc(paragraph("Thor", "in")));

        assertThat(result.preview()).isEqualTo("Thorin");
    }

    @Test
    void of_hardBreak_isASpaceInThePreviewAndNotCounted() {
        Map<String, Object> broken = Map.of("type", "paragraph",
                "content", List.of(text("one"), Map.of("type", "hardBreak"), text("two")));

        BackgroundText result = BackgroundText.of(doc(broken));

        assertThat(result).isEqualTo(new BackgroundText(6, "one two"));
    }

    @Test
    void of_horizontalRule_isASpaceInThePreviewAndNotCounted() {
        BackgroundText result = BackgroundText.of(doc(paragraph("one"), Map.of("type", "horizontalRule"), paragraph("two")));

        assertThat(result).isEqualTo(new BackgroundText(6, "one two"));
    }

    @Test
    void of_listsAndQuotes_includeTheTextOfTheirItems() {
        Map<String, Object> list = Map.of("type", "bulletList", "content", List.of(
                Map.of("type", "listItem", "content", List.of(paragraph("first"))),
                Map.of("type", "listItem", "content", List.of(paragraph("second")))));
        Map<String, Object> quote = Map.of("type", "blockquote", "content", List.of(paragraph("quoted")));

        BackgroundText result = BackgroundText.of(doc(list, quote));

        assertThat(result).isEqualTo(new BackgroundText(17, "first second quoted"));
    }

    @Test
    void of_manySpacesAndNewlines_previewCollapsesThem() {
        BackgroundText result = BackgroundText.of(doc(paragraph("  a  \n\n  b  ")));

        assertThat(result.preview()).isEqualTo("a b");
    }

    @Test
    void of_spacesAndNewlines_lengthKeepsThem() {
        BackgroundText result = BackgroundText.of(doc(paragraph("  a  \n\n  b  ")));

        assertThat(result.length()).isEqualTo(12);
    }

    @Test
    void of_charactersOutsideTheBasicPlane_countAsOneEach() {
        BackgroundText result = BackgroundText.of(doc(paragraph("a😀b")));

        assertThat(result.length()).isEqualTo(3);
    }

    @Test
    void of_longText_previewIsCutAtThreeHundredCharacters() {
        BackgroundText result = BackgroundText.of(doc(paragraph("a".repeat(500))));

        assertThat(result.preview()).hasSize(300);
    }

    @Test
    void of_longText_lengthIsTheWholeText() {
        BackgroundText result = BackgroundText.of(doc(paragraph("a".repeat(500))));

        assertThat(result.length()).isEqualTo(500);
    }

    @Test
    void of_textExactlyThreeHundredCharacters_previewIsNotShortened() {
        BackgroundText result = BackgroundText.of(doc(paragraph("a".repeat(300))));

        assertThat(result.preview()).hasSize(300);
    }

    @Test
    void of_cutInTheMiddleOfAPairOfSurrogates_neverSplitsTheCharacter() {
        BackgroundText result = BackgroundText.of(doc(paragraph("😀".repeat(400))));

        assertThat(result.preview().codePointCount(0, result.preview().length())).isEqualTo(300);
    }

    @Test
    void of_cutAfterASpace_previewHasNoTrailingSpace() {
        BackgroundText result = BackgroundText.of(doc(paragraph("a".repeat(299) + " b")));

        assertThat(result.preview()).isEqualTo("a".repeat(299));
    }

    @Test
    void of_nodeThatIsNotAnObject_isIgnored() {
        Map<String, Object> odd = Map.of("type", "doc", "content", List.of("text", paragraph("kept")));

        assertThat(BackgroundText.of(odd)).isEqualTo(new BackgroundText(4, "kept"));
    }
}
