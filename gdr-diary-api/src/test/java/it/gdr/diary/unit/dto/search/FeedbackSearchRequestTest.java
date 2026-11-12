package it.gdr.diary.unit.dto.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.ebean.ExpressionList;
import it.gdr.diary.dto.search.FeedbackSearchRequest;
import it.gdr.diary.model.Feedback;
import it.gdr.diary.model.FeedbackStatus;
import it.gdr.diary.model.FeedbackType;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeedbackSearchRequestTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    @Mock(answer = Answers.RETURNS_SELF)
    private ExpressionList<Feedback> query;

    @Test
    void applyFilter_noFilters_addsNoCondition() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();

        request.applyFilter(query, ROME);

        verifyNoInteractions(query);
    }

    @Test
    void applyFilter_type_addsEqualityOnType() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setType(FeedbackType.BUG);

        request.applyFilter(query, ROME);

        verify(query).eq("type", FeedbackType.BUG);
    }

    @Test
    void applyFilter_status_addsEqualityOnStatus() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setStatus(FeedbackStatus.NEW);

        request.applyFilter(query, ROME);

        verify(query).eq("status", FeedbackStatus.NEW);
    }

    @Test
    void applyFilter_emailWithSurroundingSpaces_searchesTrimmedEmail() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setEmail("  example.com ");

        request.applyFilter(query, ROME);

        verify(query).icontains("email", "example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void applyFilter_blankTexts_addsNoContains(String value) {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setEmail(value);

        request.applyFilter(query, ROME);

        verify(query, never()).icontains(anyString(), anyString());
    }

    @Test
    void applyFilter_createdFrom_startsAtMidnightOfThatDayInTheZone() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setCreatedFrom(LocalDate.parse("2026-11-01"));

        request.applyFilter(query, ROME);

        verify(query).ge("createdAt", Instant.parse("2026-10-31T23:00:00Z"));
    }

    @Test
    void applyFilter_createdTo_endsBeforeMidnightOfTheNextDayInTheZone() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setCreatedTo(LocalDate.parse("2026-11-30"));

        request.applyFilter(query, ROME);

        verify(query).lt("createdAt", Instant.parse("2026-11-30T23:00:00Z"));
    }

    @Test
    void applyFilter_createdToOnDaylightSavingChange_usesTheOffsetOfTheNextDay() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setCreatedTo(LocalDate.parse("2026-10-25"));

        request.applyFilter(query, ROME);

        verify(query).lt("createdAt", Instant.parse("2026-10-25T23:00:00Z"));
    }

    @Test
    void applyFilter_createdFromInUtc_startsAtUtcMidnight() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setCreatedFrom(LocalDate.parse("2026-11-01"));

        request.applyFilter(query, ZoneId.of("UTC"));

        verify(query).ge("createdAt", Instant.parse("2026-11-01T00:00:00Z"));
    }

    @Test
    void applyFilter_onlyCreatedFrom_addsNoUpperBound() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setCreatedFrom(LocalDate.parse("2026-11-01"));

        request.applyFilter(query, ROME);

        verify(query, never()).lt(anyString(), any());
    }

    @Test
    void isCreatedRangeValid_fromAfterTo_isFalse() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setCreatedFrom(LocalDate.parse("2026-11-02"));
        request.setCreatedTo(LocalDate.parse("2026-11-01"));

        assertThat(request.isCreatedRangeValid()).isFalse();
    }

    @Test
    void isCreatedRangeValid_sameDay_isTrue() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setCreatedFrom(LocalDate.parse("2026-11-01"));
        request.setCreatedTo(LocalDate.parse("2026-11-01"));

        assertThat(request.isCreatedRangeValid()).isTrue();
    }

    @Test
    void isCreatedRangeValid_onlyOneDate_isTrue() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setCreatedTo(LocalDate.parse("2026-11-01"));

        assertThat(request.isCreatedRangeValid()).isTrue();
    }

    @Test
    void applySortAndPagination_noSort_ordersByNewestFirst() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();

        request.applySortAndPagination(query, "createdAt");

        verify(query).orderBy("createdAt desc, id asc");
    }

    @Test
    void applySortAndPagination_sortByStatusAscending_ordersByStatus() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setSort("status");
        request.setDescending(false);

        request.applySortAndPagination(query, "createdAt");

        verify(query).orderBy("status asc, id asc");
    }

    @Test
    void applySortAndPagination_unknownSortKey_fallsBackToCreatedAt() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setSort("email");

        request.applySortAndPagination(query, "createdAt");

        verify(query).orderBy("createdAt desc, id asc");
    }
}
