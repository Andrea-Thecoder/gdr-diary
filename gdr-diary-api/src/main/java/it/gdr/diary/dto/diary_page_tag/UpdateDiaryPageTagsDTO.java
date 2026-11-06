package it.gdr.diary.dto.diary_page_tag;

import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.validation.ValidText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "Tags of a page of a diary. The list replaces the tags the page has now")
public record UpdateDiaryPageTagsDTO(
        @Schema(description = "New tags of the page, at most 10, each 1 to 30 characters without control characters. "
                + "Surrounding spaces are removed and two tags that differ only by case are kept once (the first one). "
                + "An empty list removes all the tags of the page",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Size(max = 10)
        List<@NotBlank @Size(max = 30) @Pattern(regexp = "^[^\\p{Cntrl}]*$") @ValidText String> tags) {
}
