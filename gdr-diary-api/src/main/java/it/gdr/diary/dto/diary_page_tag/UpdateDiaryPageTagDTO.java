package it.gdr.diary.dto.diary_page_tag;

import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.validation.ValidText;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Tag of a page of a diary. It replaces the tag the page has now")
public record UpdateDiaryPageTagDTO(
        @Schema(description = "New tag of the page, 1 to 30 characters without control characters, or null to remove the tag. "
                + "Surrounding spaces are removed. A diary can have at most 10 different tags (ignoring case): "
                + "if the diary already has the tag with another case, that spelling is kept",
                nullable = true, examples = {"combat"})
        @Size(min = 1, max = 30) @Pattern(regexp = "^(?=.*\\S)[^\\p{Cntrl}]*$") @ValidText
        String tag) {
}
