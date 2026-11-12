package it.gdr.diary.dto.search;

import io.ebean.ExpressionList;
import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.Feedback;
import it.gdr.diary.model.FeedbackStatus;
import it.gdr.diary.model.FeedbackType;
import it.gdr.diary.validation.ValidText;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.ZoneId;

@Getter
@Setter
@Schema(description = "Search filters, pagination and sorting for the feedback (newest first unless sorted otherwise)")
public class FeedbackSearchRequest extends BaseSearchRequest {

    {
        allowSort("createdAt", "createdAt");
        allowSort("type", "type");
        allowSort("status", "status");
        descending = true;
    }

    @Schema(description = "Type of feedback", examples = {"BUG"})
    private FeedbackType type;

    @Schema(description = "Processing status", examples = {"NEW"})
    private FeedbackStatus status;

    @Schema(description = "Case-insensitive text contained in the email address", examples = {"example.com"})
    @Size(max = 254)
    @ValidText
    private String email;

    @Schema(description = "Only feedback received on this day or later (from 00:00:00 of that day), date without time", examples = {"2026-11-01"})
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate createdFrom;

    @Schema(description = "Only feedback received on this day or earlier (until 23:59:59 of that day), date without time", examples = {"2026-11-30"})
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate createdTo;

    @Schema(hidden = true)
    @AssertTrue(message = "createdFrom must not be after createdTo")
    public boolean isCreatedRangeValid() {
        return createdFrom == null || createdTo == null || !createdFrom.isAfter(createdTo);
    }

    public void applyFilter(ExpressionList<Feedback> exl, ZoneId zone) {
        if (type != null) {
            exl.eq("type", type);
        }
        if (status != null) {
            exl.eq("status", status);
        }
        if (StringUtils.isNotBlank(email)) {
            exl.icontains("email", email.trim());
        }
        if (createdFrom != null) {
            exl.ge("createdAt", createdFrom.atStartOfDay(zone).toInstant());
        }
        if (createdTo != null) {
            exl.lt("createdAt", createdTo.plusDays(1).atStartOfDay(zone).toInstant());
        }
    }
}
