package it.gdr.diary.dto.diary_page;

import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.DiaryPage;

import java.util.Map;
import java.util.UUID;

@Schema(description = "A page of a diary, used in the list of the pages")
public record BaseDiaryPageDTO(
        @Schema(description = "Page identifier", examples = {"7e2b4d10-5a3c-4c8e-9f61-2a0b6d3e8c45"})
        UUID id,

        @Schema(description = "Page number inside the diary", examples = {"1"})
        Short pageNumber,

        @Schema(description = "Content of the page as a rich text document (JSON)", examples = {"{\"type\":\"doc\",\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"Day one on the road to Erebor.\"}]}]}"})
        Map<String, Object> content,

        @Schema(description = "Version of the page: to be sent back when the page is updated, so that changes made elsewhere in the meantime are detected", examples = {"3"})
        Long version,

        @Schema(description = "Tag of the page, null if the page has no tag", nullable = true, examples = {"combat"})
        String tag) {

    public static BaseDiaryPageDTO from(DiaryPage page, String tag) {
        return new BaseDiaryPageDTO(page.getId(), page.getPageNumber(), page.getContent(), page.getVersion(), tag);
    }
}
