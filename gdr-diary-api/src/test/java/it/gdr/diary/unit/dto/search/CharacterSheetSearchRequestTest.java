package it.gdr.diary.unit.dto.search;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.ebean.ExpressionList;
import it.gdr.diary.dto.search.CharacterSheetSearchRequest;
import it.gdr.diary.model.CharacterSheet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CharacterSheetSearchRequestTest {

    @Mock(answer = Answers.RETURNS_SELF)
    private ExpressionList<CharacterSheet> query;

    @Test
    void applyFilter_title_addsCaseInsensitiveContainsOnTitle() {
        CharacterSheetSearchRequest request = new CharacterSheetSearchRequest();
        request.setTitle("alira");

        request.applyFilter(query);

        verify(query).icontains("title", "alira");
    }

    @Test
    void applyFilter_titleWithSurroundingSpaces_searchesTrimmedTitle() {
        CharacterSheetSearchRequest request = new CharacterSheetSearchRequest();
        request.setTitle("  Alira, level 9 ");

        request.applyFilter(query);

        verify(query).icontains("title", "Alira, level 9");
    }

    @Test
    void applyFilter_system_addsCaseInsensitiveEqualityOnGameSystem() {
        CharacterSheetSearchRequest request = new CharacterSheetSearchRequest();
        request.setSystem("d&d 5e");

        request.applyFilter(query);

        verify(query).ieq("gameSystem", "d&d 5e");
    }

    @Test
    void applyFilter_systemWithSurroundingSpaces_searchesTrimmedSystem() {
        CharacterSheetSearchRequest request = new CharacterSheetSearchRequest();
        request.setSystem(" Pathfinder 2e  ");

        request.applyFilter(query);

        verify(query).ieq("gameSystem", "Pathfinder 2e");
    }

    @Test
    void applyFilter_bothFilters_addsBothConditions() {
        CharacterSheetSearchRequest request = new CharacterSheetSearchRequest();
        request.setTitle("alira");
        request.setSystem("D&D 5e");

        request.applyFilter(query);

        verify(query).icontains("title", "alira");
        verify(query).ieq("gameSystem", "D&D 5e");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void applyFilter_blankFilters_addsNoCondition(String value) {
        CharacterSheetSearchRequest request = new CharacterSheetSearchRequest();
        request.setTitle(value);
        request.setSystem(value);

        request.applyFilter(query);

        verifyNoInteractions(query);
    }

    @ParameterizedTest
    @CsvSource({"title,title", "system,gameSystem", "createdAt,createdAt"})
    void applySortAndPagination_allowedSortKey_ordersByMappedProperty(String sort, String property) {
        CharacterSheetSearchRequest request = new CharacterSheetSearchRequest();
        request.setSort(sort);

        request.applySortAndPagination(query, "createdAt");

        verify(query).orderBy(property + " asc, id asc");
    }

    @Test
    void applySortAndPagination_descending_ordersDescendingWithTieBreaker() {
        CharacterSheetSearchRequest request = new CharacterSheetSearchRequest();
        request.setSort("title");
        request.setDescending(true);

        request.applySortAndPagination(query, "createdAt");

        verify(query).orderBy("title desc, id asc");
    }

    @ParameterizedTest
    @ValueSource(strings = {"gameSystem", "name", "id", "document", "title; drop table character_sheet"})
    void applySortAndPagination_notAllowedSortKey_usesDefaultSort(String sort) {
        CharacterSheetSearchRequest request = new CharacterSheetSearchRequest();
        request.setSort(sort);

        request.applySortAndPagination(query, "createdAt");

        verify(query).orderBy("createdAt asc, id asc");
    }

    @Test
    void applySortAndPagination_noSort_usesDefaultSort() {
        CharacterSheetSearchRequest request = new CharacterSheetSearchRequest();

        request.applySortAndPagination(query, "createdAt");

        verify(query).orderBy("createdAt asc, id asc");
    }

    @Test
    void applySortAndPagination_defaultPagination_setsFirstPageOfTwentyRows() {
        CharacterSheetSearchRequest request = new CharacterSheetSearchRequest();

        request.applySortAndPagination(query, "createdAt");

        verify(query).setFirstRow(0);
        verify(query).setMaxRows(20);
    }

    @Test
    void applySortAndPagination_secondPage_skipsPreviousRows() {
        CharacterSheetSearchRequest request = new CharacterSheetSearchRequest();
        request.setPage(3);
        request.setSize(10);

        request.applySortAndPagination(query, "createdAt");

        verify(query).setFirstRow(20);
        verify(query).setMaxRows(10);
    }
}
