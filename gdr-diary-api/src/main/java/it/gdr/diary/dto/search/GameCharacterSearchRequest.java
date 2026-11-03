package it.gdr.diary.dto.search;

import it.gdr.diary.validation.ValidText;
import io.ebean.ExpressionList;
import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.GameCharacter;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;

@Getter
@Setter
@Schema(description = "Search filters, pagination and sorting for the characters of the logged-in user")
public class GameCharacterSearchRequest extends BaseSearchRequest {

    {
        allowSort("name", "name");
        allowSort("campaignName", "campaignName");
    }

    @Size(max = 255)
    @ValidText
    @Schema(description = "Case-insensitive text contained in the character name", examples = {"Aragorn"})
    private String name;

    @Size(max = 255)
    @ValidText
    @Schema(description = "Case-insensitive text contained in the campaign name", examples = {"Lost Mine"})
    private String campaignName;

    public void setFilter(ExpressionList<GameCharacter> exl){
        if (StringUtils.isNotBlank(name)) {
            exl.icontains("name", name.trim());
        }
        if (StringUtils.isNotBlank(campaignName)) {
            exl.icontains("campaignName", campaignName.trim());
        }
    }
}
