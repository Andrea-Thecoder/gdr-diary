package it.gdr.diary.dto.diary;

import io.swagger.v3.oas.annotations.media.Schema;
import it.gdr.diary.model.DiaryPage;

import java.util.UUID;

@Schema(description = "Bookmark of a diary: the page the reader left a marker on. Both fields are null when the diary has no bookmark")
public record BookmarkDiaryDTO(
        @Schema(description = "Identifier of the bookmarked page", example = "7e2b4d10-5a3c-4c8e-9f61-2a0b6d3e8c45", nullable = true)
        UUID pageId,
        @Schema(description = "Number of the bookmarked page inside the diary", example = "12", nullable = true)
        Short pageNumber) {

    public static BookmarkDiaryDTO from(DiaryPage page) {
        if (page == null) {
            return new BookmarkDiaryDTO(null, null);
        }
        return new BookmarkDiaryDTO(page.getId(), page.getPageNumber());
    }
}
