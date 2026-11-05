package it.gdr.diary.dto.search;

import io.ebean.ExpressionList;
import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.CharacterSheet;
import it.gdr.diary.validation.ValidText;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Getter
@Setter
@Schema(description = "Search filters, pagination and sorting for the sheets of a character")
public class CharacterSheetSearchRequest extends BaseSearchRequest {

    {
        allowSort("title", "title");
        allowSort("system", "gameSystem");
        allowSort("createdAt", "createdAt");
    }

    @Schema(description = "Case-insensitive text contained in the title", examples = {"Alira"})
    @Size(max = 100)
    @ValidText
    private String title;

    @Schema(description = "Game system, compared ignoring case", examples = {"D&D 5e"})
    @Size(max = 100)
    @ValidText
    private String system;

    public void applyFilter(ExpressionList<CharacterSheet> exl) {
        if (StringUtils.isNotBlank(title)) {
            exl.icontains("title", title.trim());
        }
        if (StringUtils.isNotBlank(system)) {
            exl.ieq("gameSystem", system.trim());
        }
    }
}
