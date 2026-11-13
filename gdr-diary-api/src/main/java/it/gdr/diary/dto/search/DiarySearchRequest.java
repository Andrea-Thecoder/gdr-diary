package it.gdr.diary.dto.search;

import it.gdr.diary.validation.ValidText;
import io.ebean.ExpressionList;
import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.Diary;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Getter
@Setter
@Schema(description = "Search filters, pagination and sorting for the diaries of a character")
public class DiarySearchRequest extends BaseSearchRequest {

    {
        allowSort("title", "title");
    }

    @Size(max = 255)
    @ValidText
    @Schema(description = "Case-insensitive text contained in the diary title", examples = {"Chapter"})
    private String title;

    @Size(max = 30)
    @ValidText
    @Schema(description = "Case-insensitive text contained in a tag of at least one page of the diary", examples = {"combat"})
    private String tag;

    public void applyFilter(ExpressionList<Diary> exl) {
        if (StringUtils.isNotBlank(title)) {
            exl.icontains("title", title.trim());
        }
    }
}
