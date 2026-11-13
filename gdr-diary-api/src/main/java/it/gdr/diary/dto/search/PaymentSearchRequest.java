package it.gdr.diary.dto.search;

import io.ebean.ExpressionList;
import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.Payment;
import it.gdr.diary.model.PaymentStatus;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Search filters, pagination and sorting for the payments of the logged-in user (newest first unless sorted otherwise, payments not yet paid last). Sort keys: paidAt, amount")
public class PaymentSearchRequest extends BaseSearchRequest {

    {
        allowSortNullsLast("paidAt", "paidAt");
        allowSort("amount", "amountCents");
        descending = true;
    }

    @Schema(description = "Status of the payment", examples = {"SUCCEEDED"})
    private PaymentStatus status;

    public void applyFilter(ExpressionList<Payment> exl) {
        if (status != null) {
            exl.eq("status", status);
        }
    }
}
