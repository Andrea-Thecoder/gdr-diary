package it.gdr.diary.unit.dto.search;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.ebean.ExpressionList;
import it.gdr.diary.dto.search.DiarySearchRequest;
import it.gdr.diary.model.Diary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiarySearchRequestTest {

    @Mock(answer = Answers.RETURNS_SELF)
    private ExpressionList<Object> query;

    @Mock(answer = Answers.RETURNS_SELF)
    private ExpressionList<Diary> diaryQuery;

    @Test
    void applySortAndPagination_titleSort_ordersByTitle() {
        DiarySearchRequest request = new DiarySearchRequest();
        request.setSort("title");
        request.setDescending(true);

        request.applySortAndPagination(query, "id");

        verify(query).orderBy("title desc, id asc");
    }

    @Test
    void applySortAndPagination_sortNotAllowed_usesDefaultSort() {
        DiarySearchRequest request = new DiarySearchRequest();
        request.setSort("description");

        request.applySortAndPagination(query, "title");

        verify(query).orderBy("title asc, id asc");
    }

    @Test
    void setFilter_titleWithSurroundingSpaces_addsCaseInsensitiveContainsOnTrimmedTitle() {
        DiarySearchRequest request = new DiarySearchRequest();
        request.setTitle("  Chapter ");

        request.setFilter(diaryQuery);

        verify(diaryQuery).icontains("title", "Chapter");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void setFilter_blankTitle_addsNoCondition(String title) {
        DiarySearchRequest request = new DiarySearchRequest();
        request.setTitle(title);

        request.setFilter(diaryQuery);

        verifyNoInteractions(diaryQuery);
    }
}
