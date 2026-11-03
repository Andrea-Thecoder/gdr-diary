package it.gdr.diary.dto.search;

import io.ebean.ExpressionList;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Getter
@Setter
@Schema(description = "Base search request with pagination and sorting parameters")
public class BaseSearchRequest {

    private static final Pattern SAFE_SORT_FIELD = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    private static final String TIE_BREAKER = ", id asc";

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Schema(hidden = true)
    private final Map<String, String> sortFields = new HashMap<>();

    @Min(1)
    @Schema(description = "Page number (1-based)", examples = {"1"})
    protected int page = 1;

    @Min(1)
    @Max(100)
    @Schema(description = "Number of results per page", examples = {"20"})
    protected int size = 20;

    @Schema(description = "Field name to sort by (see endpoint docs for allowed values)", examples = {"name"})
    protected String sort;

    @Schema(description = "Set to true to sort in descending order", examples = {"false"})
    protected boolean descending;

    protected void allowSort(String key, String property) {
        sortFields.put(key, property);
    }

    public void applySortAndPagination(ExpressionList<?> exl, String defaultSort) {
        applySort(exl, defaultSort);
        applyPagination(exl);
    }

    private void applySort(ExpressionList<?> exl, String defaultSort) {
        String direction = descending ? " desc" : " asc";
        String resolved = resolveSortField(this.getSort());
        String field = StringUtils.isNotBlank(resolved) ? resolved : defaultSort;
        if (StringUtils.isBlank(field)) {
            exl.orderById(true);
        } else if ("id".equals(field)) {
            exl.orderBy(field + direction);
        } else {
            exl.orderBy(field + direction + TIE_BREAKER);
        }
    }

    private String resolveSortField(String order) {
        if (StringUtils.isBlank(order)) {
            return null;
        }
        String key = order.trim();
        if (ObjectUtils.isNotEmpty(sortFields)) {
            return sortFields.get(key);
        }
        if (!SAFE_SORT_FIELD.matcher(key).matches()) {
            return null;
        }
        return key;
    }

    public void applyPagination(ExpressionList<?> query) {
        int rows = this.getSize();
        int offset = (int) Math.min((this.getPage() - 1L) * rows, Integer.MAX_VALUE);
        query.setFirstRow(offset).setMaxRows(rows);
    }
}
