package it.gdr.diary.unit.dto.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.ebean.ExpressionList;
import it.gdr.diary.dto.search.BaseSearchRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BaseSearchRequestTest {

    @Mock(answer = Answers.RETURNS_SELF)
    private ExpressionList<Object> query;

    private static BaseSearchRequest requestWithAllowedSort(String sort, boolean descending) {
        BaseSearchRequest request = new BaseSearchRequest() {
            {
                allowSort("name", "name");
                allowSort("campaign", "campaignName");
            }
        };
        request.setSort(sort);
        request.setDescending(descending);
        return request;
    }

    private static BaseSearchRequest requestWithoutAllowedSort(String sort) {
        BaseSearchRequest request = new BaseSearchRequest();
        request.setSort(sort);
        return request;
    }

    @Test
    void applySortAndPagination_allowedSortKey_ordersAscending() {
        BaseSearchRequest request = requestWithAllowedSort("name", false);

        request.applySortAndPagination(query, "id");

        verify(query).orderBy("name asc, id asc");
    }

    @Test
    void applySortAndPagination_allowedSortKeyDescending_ordersDescending() {
        BaseSearchRequest request = requestWithAllowedSort("name", true);

        request.applySortAndPagination(query, "id");

        verify(query).orderBy("name desc, id asc");
    }

    @Test
    void applySortAndPagination_allowedSortKey_ordersByMappedProperty() {
        BaseSearchRequest request = requestWithAllowedSort("campaign", false);

        request.applySortAndPagination(query, "id");

        verify(query).orderBy("campaignName asc, id asc");
    }

    @Test
    void applySortAndPagination_sortKeyWithSpaces_trimsKey() {
        BaseSearchRequest request = requestWithAllowedSort("  name ", false);

        request.applySortAndPagination(query, "id");

        verify(query).orderBy("name asc, id asc");
    }

    @ParameterizedTest
    @ValueSource(strings = {"createdBy", "version", "name; drop table diary", "campaignName"})
    void applySortAndPagination_sortKeyNotAllowed_usesDefaultSort(String sort) {
        BaseSearchRequest request = requestWithAllowedSort(sort, false);

        request.applySortAndPagination(query, "name");

        verify(query).orderBy("name asc, id asc");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void applySortAndPagination_blankSort_usesDefaultSort(String sort) {
        BaseSearchRequest request = requestWithAllowedSort(sort, false);

        request.applySortAndPagination(query, "title");

        verify(query).orderBy("title asc, id asc");
    }

    @Test
    void applySortAndPagination_descendingWithoutSort_ordersDefaultFieldDescending() {
        BaseSearchRequest request = requestWithAllowedSort(null, true);

        request.applySortAndPagination(query, "name");

        verify(query).orderBy("name desc, id asc");
    }

    @Test
    void applySortAndPagination_noSortAndNoDefault_ordersById() {
        BaseSearchRequest request = requestWithAllowedSort(null, false);

        request.applySortAndPagination(query, null);

        verify(query).orderById(true);
    }

    @Test
    void applySortAndPagination_noAllowedKeysAndSafeField_ordersByField() {
        BaseSearchRequest request = requestWithoutAllowedSort("title");

        request.applySortAndPagination(query, "id");

        verify(query).orderBy("title asc, id asc");
    }

    @ParameterizedTest
    @ValueSource(strings = {"title desc", "title;--", "1title", "character.owner"})
    void applySortAndPagination_noAllowedKeysAndUnsafeField_usesDefaultSort(String sort) {
        BaseSearchRequest request = requestWithoutAllowedSort(sort);

        request.applySortAndPagination(query, "id");

        verify(query).orderBy("id asc");
    }

    @Test
    void applyPagination_defaults_readsFirstTwentyRows() {
        BaseSearchRequest request = new BaseSearchRequest();

        request.applyPagination(query);

        verify(query).setFirstRow(0);
        verify(query).setMaxRows(20);
    }

    @Test
    void applyPagination_thirdPage_skipsPreviousPages() {
        BaseSearchRequest request = new BaseSearchRequest();
        request.setPage(3);
        request.setSize(25);

        request.applyPagination(query);

        verify(query).setFirstRow(50);
        verify(query).setMaxRows(25);
    }

    @Test
    void applyPagination_hugePageNumber_usesNonNegativeOffset() {
        BaseSearchRequest request = new BaseSearchRequest();
        request.setPage(Integer.MAX_VALUE);
        ArgumentCaptor<Integer> firstRow = ArgumentCaptor.forClass(Integer.class);

        request.applyPagination(query);

        verify(query).setFirstRow(firstRow.capture());
        assertThat(firstRow.getValue()).isNotNegative();
    }
}
