package it.gdr.diary.services;

import it.gdr.diary.config.DiaryPageConfig;
import it.gdr.diary.validation.StorableText;
import it.gdr.diary.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DiaryPageContentValidator {

    private static final String DOC = "doc";
    private static final String PARAGRAPH = "paragraph";
    private static final String TEXT = "text";
    private static final String HARD_BREAK = "hardBreak";
    private static final String HORIZONTAL_RULE = "horizontalRule";
    private static final String BLOCKQUOTE = "blockquote";
    private static final String BULLET_LIST = "bulletList";
    private static final String ORDERED_LIST = "orderedList";
    private static final String LIST_ITEM = "listItem";

    private static final String BOLD = "bold";
    private static final String ITALIC = "italic";
    private static final String UNDERLINE = "underline";
    private static final String TITLE = "title";
    private static final String TEXT_COLOR = "textColor";

    private static final Set<String> NODE_KEYS = Set.of("type", "content", "attrs", "marks", "text");
    private static final Set<String> MARK_KEYS = Set.of("type", "attrs");
    private static final Set<String> BLOCKS = Set.of(PARAGRAPH, BLOCKQUOTE, BULLET_LIST, ORDERED_LIST, HORIZONTAL_RULE);
    private static final Set<String> INLINES = Set.of(TEXT, HARD_BREAK);
    private static final Set<String> MARKS = Set.of(BOLD, ITALIC, UNDERLINE, TITLE, TEXT_COLOR);
    private static final Set<String> TEXT_ALIGNMENTS = Set.of("left", "center", "right", "justify");
    private static final Set<String> LIST_TYPES = Set.of("1", "a", "A", "i", "I");
    private static final Set<String> TEXT_COLORS = Set.of("red", "orange", "green", "teal", "blue", "purple", "magenta", "brown");

    private static final Map<String, NodeSpec> NODES = Map.of(
            DOC, new NodeSpec(BLOCKS, 1, null, false, Set.of()),
            PARAGRAPH, new NodeSpec(INLINES, 0, null, false, Set.of("textAlign")),
            BLOCKQUOTE, new NodeSpec(BLOCKS, 1, null, false, Set.of()),
            BULLET_LIST, new NodeSpec(Set.of(LIST_ITEM), 1, null, false, Set.of()),
            ORDERED_LIST, new NodeSpec(Set.of(LIST_ITEM), 1, null, false, Set.of("start", "type")),
            LIST_ITEM, new NodeSpec(BLOCKS, 1, PARAGRAPH, false, Set.of()),
            HORIZONTAL_RULE, new NodeSpec(Set.of(), 0, null, false, Set.of()),
            HARD_BREAK, new NodeSpec(Set.of(), 0, null, true, Set.of()),
            TEXT, new NodeSpec(Set.of(), 0, null, true, Set.of()));

    private final DiaryPageConfig config;

    public void validate(Map<String, Object> content) {
        validateNode(content, "$", 1, true);
    }

    private void validateNode(Object value, String path, int depth, boolean root) {
        if (!(value instanceof Map<?, ?> node)) {
            throw invalid(path, "a node must be an object");
        }
        if (depth > config.getMaxPageContentDepth()) {
            throw invalid(path, "nesting too deep");
        }
        requireKeys(node, NODE_KEYS, path);
        if (!(node.get("type") instanceof String type) || !NODES.containsKey(type)) {
            throw invalid(path, "unknown node type");
        }
        if (root != DOC.equals(type)) {
            throw invalid(path, root ? "the root must be a doc" : "a doc cannot be nested");
        }
        NodeSpec spec = NODES.get(type);
        validateAttrs(type, node, path, spec);
        validateMarks(node, path, spec);
        validateText(type, node, path);
        validateChildren(type, node, path, depth, spec);
    }

    private void validateAttrs(String type, Map<?, ?> node, String path, NodeSpec spec) {
        if (!node.containsKey("attrs")) {
            return;
        }
        if (!(node.get("attrs") instanceof Map<?, ?> attrs)) {
            throw invalid(path, "attrs must be an object");
        }
        requireKeys(attrs, spec.attrKeys(), path + ".attrs");
        switch (type) {
            case PARAGRAPH -> requireNullOr(attrs, "textAlign", TEXT_ALIGNMENTS, path);
            case ORDERED_LIST -> {
                requireNullOr(attrs, "type", LIST_TYPES, path);
                if (attrs.get("start") != null && !isInteger(attrs.get("start"))) {
                    throw invalid(path + ".attrs", "start must be an integer or null");
                }
            }
            default -> {
            }
        }
    }

    private void validateMarks(Map<?, ?> node, String path, NodeSpec spec) {
        if (!node.containsKey("marks")) {
            return;
        }
        if (!spec.inline()) {
            throw invalid(path, "marks are allowed only on inline nodes");
        }
        if (!(node.get("marks") instanceof List<?> marks)) {
            throw invalid(path, "marks must be an array");
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < marks.size(); i++) {
            String markPath = path + ".marks[" + i + "]";
            if (!(marks.get(i) instanceof Map<?, ?> mark)) {
                throw invalid(markPath, "a mark must be an object");
            }
            requireKeys(mark, MARK_KEYS, markPath);
            if (!(mark.get("type") instanceof String markType) || !MARKS.contains(markType)) {
                throw invalid(markPath, "unknown mark type");
            }
            if (!seen.add(markType)) {
                throw invalid(markPath, "duplicate mark");
            }
            validateMarkAttrs(markType, mark, markPath);
        }
    }

    private void validateMarkAttrs(String markType, Map<?, ?> mark, String markPath) {
        if (!mark.containsKey("attrs")) {
            if (TEXT_COLOR.equals(markType)) {
                throw invalid(markPath, "color is required");
            }
            return;
        }
        if (!(mark.get("attrs") instanceof Map<?, ?> attrs)) {
            throw invalid(markPath, "attrs must be an object");
        }
        requireKeys(attrs, markAttrKeys(markType), markPath + ".attrs");
        if (TITLE.equals(markType) && attrs.containsKey("level") && !(isNumber(attrs.get("level"), 1) || isNumber(attrs.get("level"), 2))) {
            throw invalid(markPath + ".attrs", "level must be 1 or 2");
        }
        if (TEXT_COLOR.equals(markType) && !(attrs.get("color") instanceof String color && TEXT_COLORS.contains(color))) {
            throw invalid(markPath + ".attrs", "color is not in the palette");
        }
    }

    private static Set<String> markAttrKeys(String markType) {
        return switch (markType) {
            case TITLE -> Set.of("level");
            case TEXT_COLOR -> Set.of("color");
            default -> Set.of();
        };
    }

    private void validateText(String type, Map<?, ?> node, String path) {
        if (!TEXT.equals(type)) {
            if (node.containsKey("text")) {
                throw invalid(path, "text is allowed only on text nodes");
            }
            return;
        }
        if (!(node.get("text") instanceof String text) || text.isEmpty()) {
            throw invalid(path, "a text node needs a non-empty text");
        }
        if (!StorableText.isStorable(text)) {
            throw invalid(path, "text contains characters that cannot be stored");
        }
    }

    private void validateChildren(String type, Map<?, ?> node, String path, int depth, NodeSpec spec) {
        if (!node.containsKey("content")) {
            if (spec.minChildren() > 0) {
                throw invalid(path, "content is required");
            }
            return;
        }
        if (!(node.get("content") instanceof List<?> children)) {
            throw invalid(path, "content must be an array");
        }
        if (children.size() < spec.minChildren()) {
            throw invalid(path, "content is required");
        }
        for (int i = 0; i < children.size(); i++) {
            String childPath = path + ".content[" + i + "]";
            if (children.get(i) instanceof Map<?, ?> child) {
                Object childType = child.get("type");
                if (!(childType instanceof String name) || !spec.children().contains(name)) {
                    throw invalid(childPath, "a " + type + " cannot contain this node");
                }
                if (i == 0 && spec.firstChild() != null && !spec.firstChild().equals(name)) {
                    throw invalid(childPath, "a " + type + " must start with a " + spec.firstChild());
                }
            }
            validateNode(children.get(i), childPath, depth + 1, false);
        }
    }

    private static void requireNullOr(Map<?, ?> attrs, String key, Set<String> allowed, String path) {
        Object value = attrs.get(key);
        if (value != null && !(value instanceof String text && allowed.contains(text))) {
            throw invalid(path + ".attrs", key + " has a value that is not allowed");
        }
    }

    private static void requireKeys(Map<?, ?> map, Set<String> allowed, String path) {
        for (Object key : map.keySet()) {
            if (!(key instanceof String name) || !allowed.contains(name)) {
                throw invalid(path, "unexpected property");
            }
        }
    }

    private static boolean isInteger(Object value) {
        BigDecimal number = toDecimal(value);
        return number != null && number.stripTrailingZeros().scale() <= 0;
    }

    private static boolean isNumber(Object value, int expected) {
        BigDecimal number = toDecimal(value);
        return number != null && number.compareTo(BigDecimal.valueOf(expected)) == 0;
    }

    private static BigDecimal toDecimal(Object value) {
        if (!(value instanceof Number number)) {
            return null;
        }
        try {
            return new BigDecimal(number.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }


    private static ServiceException invalid(String path, String reason) {
        return new ServiceException("Invalid page content at " + path + ": " + reason);
    }

    private record NodeSpec(Set<String> children, int minChildren, String firstChild, boolean inline, Set<String> attrKeys) {
    }
}
