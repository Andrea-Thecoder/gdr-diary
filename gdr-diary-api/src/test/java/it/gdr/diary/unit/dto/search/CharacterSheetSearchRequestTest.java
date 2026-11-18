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
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CharacterSheetSearchRequestTest {

    @Mock(answer = Answers.RETURNS_SELF)
    private ExpressionList<CharacterSheet> query;

    @Test
    void applyFilter_titleAndSystemWithSpaces_addsTrimmedConditions() {
        CharacterSheetSearchRequest request = new CharacterSheetSearchRequest();
        request.setTitle("  Alira, level 9 ");
        request.setSystem(" d&d 5e  ");

        request.applyFilter(query);

        verify(query).icontains("title", "Alira, level 9");
        verify(query).ieq("gameSystem", "d&d 5e");
    }

    @ParameterizedTest
    @NullSource
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

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"gameSystem", "title; drop table character_sheet"})
    void applySortAndPagination_notAllowedOrMissingSortKey_usesDefaultSort(String sort) {
        CharacterSheetSearchRequest request = new CharacterSheetSearchRequest();
        request.setSort(sort);

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
}
