package it.gdr.diary.unit.services;

import it.gdr.diary.config.DiaryPageConfig;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.services.DiaryPageContentValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DiaryPageContentValidatorTextColorTest {

    private static final String MARK_PATH = "$.content[0].content[0].marks[0]";

    private final DiaryPageContentValidator validator = new DiaryPageContentValidator(new DiaryPageConfig());

    @ParameterizedTest
    @ValueSource(strings = {"red", "orange", "green", "teal", "blue", "purple", "magenta", "brown"})
    void validate_textColorWithPaletteColor_accepts(String color) {
        Map<String, Object> content = pageWithMarks(List.of(colorMark(color)));

        assertThatCode(() -> validator.validate(content)).doesNotThrowAnyException();
    }

    @Test
    void validate_textColorCombinedWithOtherMarks_accepts() {
        Map<String, Object> content = pageWithMarks(List.of(Map.of("type", "bold"), colorMark("teal"), Map.of("type", "italic")));

        assertThatCode(() -> validator.validate(content)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"pink", "#ff0000", "RED", "red ", "rgb(255,0,0)", ""})
    void validate_textColorOutsideThePalette_throws(String color) {
        Map<String, Object> content = pageWithMarks(List.of(colorMark(color)));

        assertThatThrownBy(() -> validator.validate(content))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Invalid page content at " + MARK_PATH + ".attrs: color is not in the palette");
    }

    @Test
    void validate_textColorWithNumericColor_throws() {
        Map<String, Object> content = pageWithMarks(List.of(colorMark(1)));

        assertThatThrownBy(() -> validator.validate(content))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Invalid page content at " + MARK_PATH + ".attrs: color is not in the palette");
    }

    @Test
    void validate_textColorWithNullColor_throws() {
        Map<String, Object> content = pageWithMarks(List.of(colorMark(null)));

        assertThatThrownBy(() -> validator.validate(content))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Invalid page content at " + MARK_PATH + ".attrs: color is not in the palette");
    }

    @Test
    void validate_textColorWithEmptyAttrs_throws() {
        Map<String, Object> content = pageWithMarks(List.of(Map.of("type", "textColor", "attrs", Map.of())));

        assertThatThrownBy(() -> validator.validate(content))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Invalid page content at " + MARK_PATH + ".attrs: color is not in the palette");
    }

    @Test
    void validate_textColorWithoutAttrs_throws() {
        Map<String, Object> content = pageWithMarks(List.of(Map.of("type", "textColor")));

        assertThatThrownBy(() -> validator.validate(content))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Invalid page content at " + MARK_PATH + ": color is required");
    }

    @Test
    void validate_textColorWithUnexpectedAttr_throws() {
        Map<String, Object> content = pageWithMarks(List.of(Map.of("type", "textColor", "attrs", Map.of("color", "red", "style", "color: red"))));

        assertThatThrownBy(() -> validator.validate(content))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Invalid page content at " + MARK_PATH + ".attrs: unexpected property");
    }

    @Test
    void validate_textColorAppliedTwice_throws() {
        Map<String, Object> content = pageWithMarks(List.of(colorMark("red"), colorMark("blue")));

        assertThatThrownBy(() -> validator.validate(content))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Invalid page content at $.content[0].content[0].marks[1]: duplicate mark");
    }

    @Test
    void validate_textColorOnHardBreak_accepts() {
        Map<String, Object> hardBreak = Map.of("type", "hardBreak", "marks", List.of(colorMark("green")));
        Map<String, Object> content = Map.of("type", "doc", "content", List.of(
                Map.of("type", "paragraph", "content", List.of(hardBreak))));

        assertThatCode(() -> validator.validate(content)).doesNotThrowAnyException();
    }

    @Test
    void validate_levelAttrOnTextColor_throws() {
        Map<String, Object> content = pageWithMarks(List.of(Map.of("type", "textColor", "attrs", Map.of("level", 1))));

        assertThatThrownBy(() -> validator.validate(content))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Invalid page content at " + MARK_PATH + ".attrs: unexpected property");
    }

    private static Map<String, Object> colorMark(Object color) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("color", color);
        Map<String, Object> mark = new HashMap<>();
        mark.put("type", "textColor");
        mark.put("attrs", attrs);
        return mark;
    }

    private static Map<String, Object> pageWithMarks(List<Map<String, Object>> marks) {
        Map<String, Object> text = Map.of("type", "text", "text", "Testo", "marks", marks);
        Map<String, Object> paragraph = Map.of("type", "paragraph", "content", List.of(text));
        return Map.of("type", "doc", "content", List.of(paragraph));
    }
}
