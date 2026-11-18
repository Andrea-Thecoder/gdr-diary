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
import org.junit.jupiter.params.provider.CsvSource;
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
    void applyFilter_allFilters_addsEveryConditionInTheZone() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setType(FeedbackType.BUG);
        request.setStatus(FeedbackStatus.NEW);
        request.setEmail("  example.com ");
        request.setCreatedFrom(LocalDate.parse("2026-11-01"));
        request.setCreatedTo(LocalDate.parse("2026-11-30"));

        request.applyFilter(query, ROME);

        verify(query).eq("type", FeedbackType.BUG);
        verify(query).eq("status", FeedbackStatus.NEW);
        verify(query).icontains("email", "example.com");
        verify(query).ge("createdAt", Instant.parse("2026-10-31T23:00:00Z"));
        verify(query).lt("createdAt", Instant.parse("2026-11-30T23:00:00Z"));
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
    void applyFilter_createdToOnDaylightSavingChange_usesTheOffsetOfTheNextDay() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setCreatedTo(LocalDate.parse("2026-10-25"));

        request.applyFilter(query, ROME);

        verify(query).lt("createdAt", Instant.parse("2026-10-25T23:00:00Z"));
    }

    @Test
    void applyFilter_onlyCreatedFromInUtc_startsAtUtcMidnightWithoutUpperBound() {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setCreatedFrom(LocalDate.parse("2026-11-01"));

        request.applyFilter(query, ZoneId.of("UTC"));

        verify(query).ge("createdAt", Instant.parse("2026-11-01T00:00:00Z"));
        verify(query, never()).lt(anyString(), any());
    }

    @ParameterizedTest
    @CsvSource(value = {"2026-11-02,2026-11-01,false", "2026-11-01,2026-11-01,true", ",2026-11-01,true"})
    void isCreatedRangeValid_dates_tellsIfTheRangeIsValid(LocalDate from, LocalDate to, boolean expected) {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setCreatedFrom(from);
        request.setCreatedTo(to);

        boolean valid = request.isCreatedRangeValid();

        assertThat(valid).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(value = {";true;createdAt desc, id asc", "status;false;status asc, id asc", "email;true;createdAt desc, id asc"},
            delimiter = ';')
    void applySortAndPagination_sortKey_ordersByAllowedKeyOrNewestFirst(String sort, boolean descending, String expectedOrder) {
        FeedbackSearchRequest request = new FeedbackSearchRequest();
        request.setSort(sort);
        request.setDescending(descending);

        request.applySortAndPagination(query, "createdAt");

        verify(query).orderBy(expectedOrder);
    }
}
