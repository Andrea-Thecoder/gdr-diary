package it.gdr.diary.dto.search;

import io.ebean.ExpressionList;
import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.DiaryPageTag;
import it.gdr.diary.validation.ValidText;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Getter
@Setter
@Schema(description = "Search filter and pagination for the tags of a diary, always sorted by the number of the first page that has each tag")
public class DiaryTagSearchRequest {

    @Size(max = 30)
    @ValidText
    @Schema(description = "Case-insensitive text contained in the tag", examples = {"comb"})
    private String name;

    @Min(1)
    @Schema(description = "Page number (1-based)", examples = {"1"})
    private int page = 1;

    @Min(1)
    @Max(100)
    @Schema(description = "Number of results per page", examples = {"20"})
    private int size = 20;

    public void applyFilter(ExpressionList<DiaryPageTag> exl) {
        if (StringUtils.isNotBlank(name)) {
            exl.icontains("name", name.trim());
        }
    }
}
