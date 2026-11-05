package it.gdr.diary.unit.dto.search;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.ebean.ExpressionList;
import it.gdr.diary.dto.search.GameCharacterSearchRequest;
import it.gdr.diary.model.GameCharacter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GameCharacterSearchRequestTest {

    @Mock(answer = Answers.RETURNS_SELF)
    private ExpressionList<GameCharacter> query;

    @Test
    void setFilter_name_addsCaseInsensitiveContainsOnName() {
        GameCharacterSearchRequest request = new GameCharacterSearchRequest();
        request.setName("ara");

        request.applyFilter(query);

        verify(query).icontains("name", "ara");
    }

    @Test
    void setFilter_campaignName_addsCaseInsensitiveContainsOnCampaign() {
        GameCharacterSearchRequest request = new GameCharacterSearchRequest();
        request.setCampaignName("mine");

        request.applyFilter(query);

        verify(query).icontains("campaignName", "mine");
    }

    @Test
    void setFilter_bothFilters_addsBothConditions() {
        GameCharacterSearchRequest request = new GameCharacterSearchRequest();
        request.setName("ara");
        request.setCampaignName("mine");

        request.applyFilter(query);

        verify(query).icontains("name", "ara");
        verify(query).icontains("campaignName", "mine");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void setFilter_blankFilters_addsNoCondition(String value) {
        GameCharacterSearchRequest request = new GameCharacterSearchRequest();
        request.setName(value);
        request.setCampaignName(value);

        request.applyFilter(query);

        verifyNoInteractions(query);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "campaignName"})
    void applySortAndPagination_allowedSortKey_ordersByKey(String sort) {
        GameCharacterSearchRequest request = new GameCharacterSearchRequest();
        request.setSort(sort);

        request.applySortAndPagination(query, "name");

        verify(query).orderBy(sort + " asc, id asc");
    }

    @Test
    void applySortAndPagination_auditFieldAsSort_usesDefaultSort() {
        GameCharacterSearchRequest request = new GameCharacterSearchRequest();
        request.setSort("createdAt");

        request.applySortAndPagination(query, "name");

        verify(query).orderBy("name asc, id asc");
    }

    @Test
    void applySortAndPagination_noSort_usesDefaultSort() {
        GameCharacterSearchRequest request = new GameCharacterSearchRequest();

        request.applySortAndPagination(query, "name");

        verify(query).orderBy("name asc, id asc");
    }

    @Test
    void setFilter_nameWithSurroundingSpaces_searchesTrimmedName() {
        GameCharacterSearchRequest request = new GameCharacterSearchRequest();
        request.setName(" Aragorn ");

        request.applyFilter(query);

        verify(query).icontains("name", "Aragorn");
    }
}
