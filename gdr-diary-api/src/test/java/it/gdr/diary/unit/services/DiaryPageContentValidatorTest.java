package it.gdr.diary.unit.services;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import it.gdr.diary.config.DiaryPageConfig;
import it.gdr.diary.exception.ServiceException;
import it.gdr.diary.services.DiaryPageContentValidator;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class DiaryPageContentValidatorTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final DiaryPageContentValidator validator = new DiaryPageContentValidator(new DiaryPageConfig());

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parse(String json) {
        return MAPPER.readValue(json, Map.class);
    }

    private static String doc(String blocks) {
        return "{\"type\":\"doc\",\"content\":[" + blocks + "]}";
    }

    private static String paragraph(String inlines) {
        return "{\"type\":\"paragraph\",\"content\":[" + inlines + "]}";
    }

    private static String text(String value) {
        return "{\"type\":\"text\",\"text\":\"" + value + "\"}";
    }

    private static String nestedQuotes(int levels) {
        String open = "{\"type\":\"blockquote\",\"content\":[".repeat(levels);
        String close = "]}".repeat(levels);
        return doc(open + "{\"type\":\"paragraph\"}" + close);
    }

    private static String orderedList(String attrs) {
        return doc("{\"type\":\"orderedList\",\"attrs\":" + attrs + ",\"content\":[{\"type\":\"listItem\",\"content\":[{\"type\":\"paragraph\"}]}]}");
    }

    private static String textWithMarks(String marks) {
        return doc(paragraph("{\"type\":\"text\",\"text\":\"x\",\"marks\":" + marks + "}"));
    }

    static Stream<Arguments> editorDocuments() {
        return Stream.of(
                argumentSet("empty paragraph", "{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}"),
                argumentSet("paragraph with null alignment", doc("{\"type\":\"paragraph\",\"attrs\":{\"textAlign\":null},\"content\":[" + text("Day one") + "]}")),
                argumentSet("all marks on text", textWithMarks("[{\"type\":\"bold\"},{\"type\":\"italic\"},{\"type\":\"underline\"},{\"type\":\"title\",\"attrs\":{\"level\":2}}]")),
                argumentSet("title without attrs", textWithMarks("[{\"type\":\"title\"}]")),
                argumentSet("marked hard break", doc(paragraph(text("a") + ",{\"type\":\"hardBreak\",\"marks\":[{\"type\":\"bold\"}]}," + text("b")))),
                argumentSet("horizontal rule", doc("{\"type\":\"horizontalRule\"}," + paragraph(text("after")))),
                argumentSet("nested blockquotes", doc("{\"type\":\"blockquote\",\"content\":[" + paragraph(text("quote")) + ",{\"type\":\"blockquote\",\"content\":[" + paragraph(text("nested")) + "]}]}")),
                argumentSet("nested lists", doc("{\"type\":\"bulletList\",\"content\":[{\"type\":\"listItem\",\"content\":[" + paragraph(text("a")) + ",{\"type\":\"orderedList\",\"attrs\":{\"start\":3,\"type\":\"a\"},\"content\":[{\"type\":\"listItem\",\"content\":[" + paragraph(text("b")) + "]}]}]}]}")),
                argumentSet("ordered list with null attrs", orderedList("{\"start\":null,\"type\":null}")),
                argumentSet("ordered list with negative start", orderedList("{\"start\":-5,\"type\":\"I\"}")),
                argumentSet("ordered list with huge start", orderedList("{\"start\":100000000000000000000,\"type\":\"1\"}")),
                argumentSet("markup inside text", doc(paragraph(text("<script>alert(1)</script> <img src=x onerror=alert(1)>")))),
                argumentSet("surrogate pair", doc(paragraph(text("\\ud83d\\udc09 dragon")))),
                argumentSet("nesting within limit", nestedQuotes(10)));
    }

    static Stream<Arguments> nodesNotInEditor() {
        return Stream.of(
                argumentSet("heading", doc("{\"type\":\"heading\",\"attrs\":{\"level\":1},\"content\":[" + text("x") + "]}")),
                argumentSet("code block", doc("{\"type\":\"codeBlock\",\"content\":[" + text("x") + "]}")),
                argumentSet("image", doc("{\"type\":\"image\",\"attrs\":{\"src\":\"javascript:alert(1)\"}}")),
                argumentSet("table", doc("{\"type\":\"table\"}")),
                argumentSet("script", doc("{\"type\":\"script\"}")),
                argumentSet("null type", doc("{\"type\":null}")),
                argumentSet("missing type", doc("{}")));
    }

    static Stream<Arguments> marksNotInEditor() {
        return Stream.of(
                argumentSet("strike", textWithMarks("[{\"type\":\"strike\"}]")),
                argumentSet("code", textWithMarks("[{\"type\":\"code\"}]")),
                argumentSet("link", textWithMarks("[{\"type\":\"link\",\"attrs\":{\"href\":\"javascript:alert(1)\"}}]")),
                argumentSet("text style", textWithMarks("[{\"type\":\"textStyle\",\"attrs\":{\"color\":\"red\"}}]")));
    }

    static Stream<Arguments> unexpectedProperties() {
        return Stream.of(
                argumentSet("class on doc", "{\"type\":\"doc\",\"class\":\"x\",\"content\":[{\"type\":\"paragraph\"}]}"),
                argumentSet("style on paragraph", doc("{\"type\":\"paragraph\",\"style\":\"color:red\"}")),
                argumentSet("style in paragraph attrs", doc("{\"type\":\"paragraph\",\"attrs\":{\"style\":\"color:red\"}}")),
                argumentSet("extra paragraph attr", doc("{\"type\":\"paragraph\",\"attrs\":{\"textAlign\":null,\"class\":\"x\"}}")),
                argumentSet("blockquote attrs", doc("{\"type\":\"blockquote\",\"attrs\":{\"cite\":\"x\"},\"content\":[{\"type\":\"paragraph\"}]}")),
                argumentSet("bold attrs", textWithMarks("[{\"type\":\"bold\",\"attrs\":{\"class\":\"x\"}}]")),
                argumentSet("style on mark", textWithMarks("[{\"type\":\"bold\",\"style\":\"x\"}]")),
                argumentSet("extra title attr", textWithMarks("[{\"type\":\"title\",\"attrs\":{\"level\":1,\"style\":\"x\"}}]")));
    }

    static Stream<Arguments> attributeValuesNotAllowed() {
        return Stream.of(
                argumentSet("css injection in alignment", doc("{\"type\":\"paragraph\",\"attrs\":{\"textAlign\":\"left; background:url(https://evil.example/x)\"}}")),
                argumentSet("unknown alignment", doc("{\"type\":\"paragraph\",\"attrs\":{\"textAlign\":\"start\"}}")),
                argumentSet("numeric alignment", doc("{\"type\":\"paragraph\",\"attrs\":{\"textAlign\":1}}")),
                argumentSet("unknown list type", orderedList("{\"type\":\"disc\"}")),
                argumentSet("string start", orderedList("{\"start\":\"1\"}")),
                argumentSet("decimal start", orderedList("{\"start\":1.5}")),
                argumentSet("object start", orderedList("{\"start\":{}}")),
                argumentSet("title level 3", textWithMarks("[{\"type\":\"title\",\"attrs\":{\"level\":3}}]")),
                argumentSet("string title level", textWithMarks("[{\"type\":\"title\",\"attrs\":{\"level\":\"1\"}}]")),
                argumentSet("decimal title level", textWithMarks("[{\"type\":\"title\",\"attrs\":{\"level\":1.5}}]")));
    }

    static Stream<Arguments> malformedTextNodes() {
        return Stream.of(
                argumentSet("missing text", doc(paragraph("{\"type\":\"text\"}"))),
                argumentSet("empty text", doc(paragraph("{\"type\":\"text\",\"text\":\"\"}"))),
                argumentSet("numeric text", doc(paragraph("{\"type\":\"text\",\"text\":5}"))),
                argumentSet("text with content", doc(paragraph("{\"type\":\"text\",\"text\":\"x\",\"content\":[{\"type\":\"text\",\"text\":\"y\"}]}"))),
                argumentSet("text on paragraph", doc("{\"type\":\"paragraph\",\"text\":\"x\"}")),
                argumentSet("text on hard break", doc(paragraph("{\"type\":\"hardBreak\",\"text\":\"x\"}"))));
    }

    static Stream<Arguments> charactersNotStorable() {
        return Stream.of(
                argumentSet("NUL character", doc(paragraph(text("a\\u0000b")))),
                argumentSet("lone high surrogate", doc(paragraph(text("a\\ud83d")))),
                argumentSet("lone low surrogate", doc(paragraph(text("\\ude00a")))));
    }

    static Stream<Arguments> misplacedOrDuplicatedMarks() {
        return Stream.of(
                argumentSet("marks on paragraph", doc("{\"type\":\"paragraph\",\"marks\":[{\"type\":\"bold\"}]}")),
                argumentSet("marks on horizontal rule", doc("{\"type\":\"horizontalRule\",\"marks\":[{\"type\":\"bold\"}]}")),
                argumentSet("duplicated mark", textWithMarks("[{\"type\":\"bold\"},{\"type\":\"bold\"}]")),
                argumentSet("marks as object", textWithMarks("{\"type\":\"bold\"}")),
                argumentSet("mark as string", textWithMarks("[\"bold\"]")));
    }

    static Stream<Arguments> wrongStructures() {
        return Stream.of(
                argumentSet("paragraph as root", "{\"type\":\"paragraph\"}"),
                argumentSet("doc without content", "{\"type\":\"doc\"}"),
                argumentSet("doc with empty content", "{\"type\":\"doc\",\"content\":[]}"),
                argumentSet("doc content as object", "{\"type\":\"doc\",\"content\":{}}"),
                argumentSet("text directly in doc", doc(text("loose text"))),
                argumentSet("nested doc", doc("{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\"}]}")),
                argumentSet("list item outside list", doc("{\"type\":\"listItem\",\"content\":[{\"type\":\"paragraph\"}]}")),
                argumentSet("paragraph directly in list", doc("{\"type\":\"bulletList\",\"content\":[{\"type\":\"paragraph\"}]}")),
                argumentSet("list without content", doc("{\"type\":\"bulletList\"}")),
                argumentSet("list with empty content", doc("{\"type\":\"bulletList\",\"content\":[]}")),
                argumentSet("empty list item", doc("{\"type\":\"bulletList\",\"content\":[{\"type\":\"listItem\"}]}")),
                argumentSet("list item starting with blockquote", doc("{\"type\":\"bulletList\",\"content\":[{\"type\":\"listItem\",\"content\":[{\"type\":\"blockquote\",\"content\":[{\"type\":\"paragraph\"}]}]}]}")),
                argumentSet("empty blockquote", doc("{\"type\":\"blockquote\"}")),
                argumentSet("paragraph in paragraph", doc("{\"type\":\"paragraph\",\"content\":[{\"type\":\"paragraph\"}]}")),
                argumentSet("content in horizontal rule", doc("{\"type\":\"horizontalRule\",\"content\":[{\"type\":\"paragraph\"}]}")),
                argumentSet("paragraph content as object", doc("{\"type\":\"paragraph\",\"content\":{}}")),
                argumentSet("string child", doc("{\"type\":\"paragraph\",\"content\":[\"x\"]}")),
                argumentSet("null child", doc("{\"type\":\"paragraph\",\"content\":[null]}")),
                argumentSet("attrs as array", doc("{\"type\":\"paragraph\",\"attrs\":[]}")),
                argumentSet("null attrs", doc("{\"type\":\"paragraph\",\"attrs\":null}")),
                argumentSet("string node", doc("\"paragraph\"")));
    }

    @ParameterizedTest
    @MethodSource("editorDocuments")
    void validate_documentProducedByEditor_accepts(String json) {
        Map<String, Object> content = parse(json);

        assertThatCode(() -> validator.validate(content)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"left", "center", "right", "justify"})
    void validate_alignmentOfferedByEditor_accepts(String alignment) {
        Map<String, Object> content = parse(doc("{\"type\":\"paragraph\",\"attrs\":{\"textAlign\":\"" + alignment + "\"}}"));

        assertThatCode(() -> validator.validate(content)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "a", "A", "i", "I"})
    void validate_orderedListTypeOfferedByEditor_accepts(String type) {
        Map<String, Object> content = parse(orderedList("{\"start\":1,\"type\":\"" + type + "\"}"));

        assertThatCode(() -> validator.validate(content)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("nodesNotInEditor")
    void validate_nodeNotInEditor_throwsServiceException(String json) {
        Map<String, Object> content = parse(json);

        assertThatThrownBy(() -> validator.validate(content)).isInstanceOf(ServiceException.class);
    }

    @ParameterizedTest
    @MethodSource("marksNotInEditor")
    void validate_markNotInEditor_throwsServiceException(String json) {
        Map<String, Object> content = parse(json);

        assertThatThrownBy(() -> validator.validate(content)).isInstanceOf(ServiceException.class);
    }

    @ParameterizedTest
    @MethodSource("unexpectedProperties")
    void validate_unexpectedProperty_throwsServiceException(String json) {
        Map<String, Object> content = parse(json);

        assertThatThrownBy(() -> validator.validate(content)).isInstanceOf(ServiceException.class);
    }

    @ParameterizedTest
    @MethodSource("attributeValuesNotAllowed")
    void validate_attributeValueNotAllowed_throwsServiceException(String json) {
        Map<String, Object> content = parse(json);

        assertThatThrownBy(() -> validator.validate(content)).isInstanceOf(ServiceException.class);
    }

    @ParameterizedTest
    @MethodSource("malformedTextNodes")
    void validate_malformedTextNode_throwsServiceException(String json) {
        Map<String, Object> content = parse(json);

        assertThatThrownBy(() -> validator.validate(content)).isInstanceOf(ServiceException.class);
    }

    @ParameterizedTest
    @MethodSource("charactersNotStorable")
    void validate_characterNotStorableInJsonb_throwsServiceException(String json) {
        Map<String, Object> content = parse(json);

        assertThatThrownBy(() -> validator.validate(content)).isInstanceOf(ServiceException.class);
    }

    @ParameterizedTest
    @MethodSource("misplacedOrDuplicatedMarks")
    void validate_misplacedOrDuplicatedMarks_throwsServiceException(String json) {
        Map<String, Object> content = parse(json);

        assertThatThrownBy(() -> validator.validate(content)).isInstanceOf(ServiceException.class);
    }

    @ParameterizedTest
    @MethodSource("wrongStructures")
    void validate_wrongStructure_throwsServiceException(String json) {
        Map<String, Object> content = parse(json);

        assertThatThrownBy(() -> validator.validate(content)).isInstanceOf(ServiceException.class);
    }

    @Test
    void validate_nestingBeyondMaxDepth_throwsServiceException() {
        Map<String, Object> content = parse(nestedQuotes(60));

        assertThatThrownBy(() -> validator.validate(content))
                .isInstanceOf(ServiceException.class)
                .hasMessageEndingWith("nesting too deep");
    }

    @Test
    void validate_nestingWithinConfiguredDepth_accepts() {
        DiaryPageConfig config = new DiaryPageConfig();
        config.setMaxPageContentDepth(5);
        DiaryPageContentValidator shallowValidator = new DiaryPageContentValidator(config);
        Map<String, Object> content = parse(nestedQuotes(2));

        assertThatCode(() -> shallowValidator.validate(content)).doesNotThrowAnyException();
    }

    @Test
    void validate_nestingBeyondConfiguredDepth_throwsServiceException() {
        DiaryPageConfig config = new DiaryPageConfig();
        config.setMaxPageContentDepth(5);
        DiaryPageContentValidator shallowValidator = new DiaryPageContentValidator(config);
        Map<String, Object> content = parse(nestedQuotes(6));

        assertThatThrownBy(() -> shallowValidator.validate(content)).isInstanceOf(ServiceException.class);
    }

    @Test
    void validate_nodeNotAllowedInDoc_reportsPathAndReason() {
        Map<String, Object> content = parse(doc("{\"type\":\"heading\"}"));

        assertThatThrownBy(() -> validator.validate(content))
                .isInstanceOf(ServiceException.class)
                .hasMessage("Invalid page content at $.content[0]: a doc cannot contain this node");
    }
}
