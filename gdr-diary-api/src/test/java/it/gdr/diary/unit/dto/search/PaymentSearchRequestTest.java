package it.gdr.diary.unit.dto.search;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.ebean.ExpressionList;
import it.gdr.diary.dto.search.PaymentSearchRequest;
import it.gdr.diary.model.Payment;
import it.gdr.diary.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentSearchRequestTest {

    @Mock(answer = Answers.RETURNS_SELF)
    private ExpressionList<Payment> query;

    @Test
    void applyFilter_status_addsEqualityOnStatus() {
        PaymentSearchRequest request = new PaymentSearchRequest();
        request.setStatus(PaymentStatus.REFUNDED);

        request.applyFilter(query);

        verify(query).eq("status", PaymentStatus.REFUNDED);
    }

    @Test
    void applyFilter_noStatus_addsNoCondition() {
        PaymentSearchRequest request = new PaymentSearchRequest();

        request.applyFilter(query);

        verifyNoInteractions(query);
    }

    @Test
    void applySortAndPagination_noSort_ordersByPaidAtDescendingNullsLast() {
        PaymentSearchRequest request = new PaymentSearchRequest();

        request.applySortAndPagination(query, "paidAt");

        verify(query).orderBy("paidAt desc nulls last, id asc");
    }

    @Test
    void applySortAndPagination_paidAtAscending_ordersAscendingNullsLast() {
        PaymentSearchRequest request = new PaymentSearchRequest();
        request.setSort("paidAt");
        request.setDescending(false);

        request.applySortAndPagination(query, "paidAt");

        verify(query).orderBy("paidAt asc nulls last, id asc");
    }

    @ParameterizedTest
    @CsvSource({"true,desc", "false,asc"})
    void applySortAndPagination_amount_ordersByAmountCents(boolean descending, String direction) {
        PaymentSearchRequest request = new PaymentSearchRequest();
        request.setSort("amount");
        request.setDescending(descending);

        request.applySortAndPagination(query, "paidAt");

        verify(query).orderBy("amountCents " + direction + ", id asc");
    }

    @ParameterizedTest
    @ValueSource(strings = {"amountCents", "status", "id", "appUser", "retainUntil", "paidAt; drop table payment"})
    void applySortAndPagination_notAllowedSortKey_usesDefaultSort(String sort) {
        PaymentSearchRequest request = new PaymentSearchRequest();
        request.setSort(sort);

        request.applySortAndPagination(query, "paidAt");

        verify(query).orderBy("paidAt desc nulls last, id asc");
    }

    @Test
    void applySortAndPagination_secondPage_skipsPreviousRows() {
        PaymentSearchRequest request = new PaymentSearchRequest();
        request.setPage(3);
        request.setSize(10);

        request.applySortAndPagination(query, "paidAt");

        verify(query).setFirstRow(20);
        verify(query).setMaxRows(10);
    }
}
