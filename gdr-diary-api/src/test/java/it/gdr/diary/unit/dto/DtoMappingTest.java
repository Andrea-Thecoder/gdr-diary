package it.gdr.diary.unit.dto;

import static org.assertj.core.api.Assertions.assertThat;

import it.gdr.diary.dto.app_user.DetailAppUserDTO;
import it.gdr.diary.dto.character_sheet.BaseCharacterSheetDTO;
import it.gdr.diary.dto.character_sheet.DetailCharacterSheetDTO;
import it.gdr.diary.dto.diary.BaseDiaryDTO;
import it.gdr.diary.dto.diary.BookmarkDiaryDTO;
import it.gdr.diary.dto.diary.DetailDiaryDTO;
import it.gdr.diary.dto.diary_page.BaseDiaryPageDTO;
import it.gdr.diary.dto.game_character.BaseGameCharacterDTO;
import it.gdr.diary.dto.game_character.DetailGameCharacterDTO;
import it.gdr.diary.model.AppUser;
import it.gdr.diary.model.CharacterSheet;
import it.gdr.diary.model.Diary;
import it.gdr.diary.model.DiaryPage;
import it.gdr.diary.model.Document;
import it.gdr.diary.model.GameCharacter;
import it.gdr.diary.model.GameCharacterKind;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DtoMappingTest {

    private static final UUID CHARACTER_ID = UUID.fromString("0b8f9c1e-6f0a-4b9e-9c55-3b7a3a1d2f10");
    private static final UUID DIARY_ID = UUID.fromString("5d1e7c0a-2b43-4f6e-8a79-91c2d6f0b8a4");
    private static final UUID DOCUMENT_ID = UUID.fromString("3f2b1c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d");
    private static final String URL = "https://storage.example/signed";

    private static Document document() {
        Document document = new Document();
        document.setId(DOCUMENT_ID);
        document.setBucket("diary-documents");
        document.setObjectKey("secret-key");
        return document;
    }

    private static GameCharacter character() {
        GameCharacter character = new GameCharacter();
        character.setId(CHARACTER_ID);
        character.setName("Aragorn");
        character.setKind(GameCharacterKind.PLAYER);
        character.setDescription("Ranger");
        character.setCampaignName("Lost Mine");
        character.setBackgroundColor("#1A2B3C");
        return character;
    }

    private static Diary diary() {
        Diary diary = new Diary();
        diary.setId(DIARY_ID);
        diary.setCharacter(character());
        diary.setTitle("Chapter one");
        diary.setDescription("The journey begins");
        diary.setCoverColor("#000000");
        diary.setFont("Lora");
        diary.setFontSize((short) 16);
        return diary;
    }

    @Test
    void detailAppUserFrom_nullSettings_returnsEmptySettings() {
        AppUser user = new AppUser();

        DetailAppUserDTO dto = DetailAppUserDTO.from(user, null);

        assertThat(dto.settings()).isEmpty();
    }

    @Test
    void detailAppUserFrom_storedUser_copiesEmailPremiumSettingsAndUrl() {
        AppUser user = new AppUser();
        user.setEmail("frodo@shire.example");
        user.setPremium(true);
        user.setSettings(Map.of("font", "Lora"));

        DetailAppUserDTO dto = DetailAppUserDTO.from(user, URL);

        assertThat(dto).isEqualTo(new DetailAppUserDTO("frodo@shire.example", true, Map.of("font", "Lora"), URL));
    }

    @Test
    void baseGameCharacterFrom_character_copiesSummaryFields() {
        BaseGameCharacterDTO dto = BaseGameCharacterDTO.from(character(), URL);

        assertThat(dto).isEqualTo(new BaseGameCharacterDTO(CHARACTER_ID, "Aragorn", GameCharacterKind.PLAYER, "Lost Mine", "#1A2B3C", URL));
    }

    @Test
    void detailGameCharacterFrom_characterWithAvatar_exposesAvatarDocumentId() {
        GameCharacter character = character();
        character.setAvatarDocument(document());

        DetailGameCharacterDTO dto = DetailGameCharacterDTO.from(character, URL);

        assertThat(dto.avatarDocumentId()).isEqualTo(DOCUMENT_ID);
    }

    @Test
    void detailGameCharacterFrom_characterWithoutAvatar_returnsNullAvatarDocumentId() {
        DetailGameCharacterDTO dto = DetailGameCharacterDTO.from(character(), null);

        assertThat(dto.avatarDocumentId()).isNull();
    }

    @Test
    void detailGameCharacterFrom_character_copiesAllFields() {
        DetailGameCharacterDTO dto = DetailGameCharacterDTO.from(character(), URL);

        assertThat(dto).isEqualTo(new DetailGameCharacterDTO(CHARACTER_ID, "Aragorn", GameCharacterKind.PLAYER, "Ranger", "Lost Mine", "#1A2B3C", null, URL));
    }

    @Test
    void baseDiaryFrom_diary_copiesSummaryFieldsAndCharacterId() {
        BaseDiaryDTO dto = BaseDiaryDTO.from(diary(), URL);

        assertThat(dto).isEqualTo(new BaseDiaryDTO(DIARY_ID, CHARACTER_ID, "Chapter one", "#000000", URL));
    }

    @Test
    void detailDiaryFrom_diaryWithCover_exposesCoverDocumentId() {
        Diary diary = diary();
        diary.setCoverDocument(document());

        DetailDiaryDTO dto = DetailDiaryDTO.from(diary, URL);

        assertThat(dto.coverDocumentId()).isEqualTo(DOCUMENT_ID);
    }

    @Test
    void detailDiaryFrom_diary_copiesAllFields() {
        DetailDiaryDTO dto = DetailDiaryDTO.from(diary(), URL);

        assertThat(dto).isEqualTo(new DetailDiaryDTO(DIARY_ID, CHARACTER_ID, "Chapter one", "The journey begins", "#000000", "Lora", (short) 16, null, URL));
    }

    @Test
    void baseDiaryPageFrom_page_copiesNumberContentAndVersion() {
        DiaryPage page = new DiaryPage();
        UUID pageId = UUID.randomUUID();
        page.setId(pageId);
        page.setPageNumber((short) 3);
        page.setContent(Map.of("type", "doc"));
        page.setVersion(7L);

        BaseDiaryPageDTO dto = BaseDiaryPageDTO.from(page, "elfo");

        assertThat(dto).isEqualTo(new BaseDiaryPageDTO(pageId, (short) 3, Map.of("type", "doc"), 7L, "elfo"));
    }

    @Test
    void bookmarkFrom_page_returnsIdAndNumber() {
        DiaryPage page = new DiaryPage();
        UUID pageId = UUID.randomUUID();
        page.setId(pageId);
        page.setPageNumber((short) 12);

        BookmarkDiaryDTO dto = BookmarkDiaryDTO.from(page);

        assertThat(dto).isEqualTo(new BookmarkDiaryDTO(pageId, (short) 12));
    }

    @Test
    void bookmarkFrom_noPage_returnsEmptyBookmark() {
        BookmarkDiaryDTO dto = BookmarkDiaryDTO.from(null);

        assertThat(dto).isEqualTo(new BookmarkDiaryDTO(null, null));
    }

    private static final UUID SHEET_ID = UUID.fromString("7d1e5a52-9a0c-4b57-8c64-0f7a1d9a4e21");
    private static final Instant SHEET_CREATED_AT = Instant.parse("2026-11-05T10:15:30Z");
    private static final String DOWNLOAD_URL = "https://storage.example/download";

    private static CharacterSheet sheet() {
        Document pdf = document();
        pdf.setName("alira.pdf");
        pdf.setSize(2048L);
        CharacterSheet sheet = new CharacterSheet();
        sheet.setId(SHEET_ID);
        sheet.setCharacter(character());
        sheet.setDocument(pdf);
        sheet.setTitle("Alira, level 9");
        sheet.setGameSystem("D&D 5e");
        sheet.setCreatedAt(SHEET_CREATED_AT);
        return sheet;
    }

    @Test
    void baseCharacterSheetFrom_sheet_copiesSummaryFieldsAndUrls() {
        BaseCharacterSheetDTO dto = BaseCharacterSheetDTO.from(sheet(), URL, DOWNLOAD_URL);

        assertThat(dto).isEqualTo(new BaseCharacterSheetDTO(SHEET_ID, CHARACTER_ID, "Alira, level 9", "D&D 5e", 2048L,
                SHEET_CREATED_AT, URL, DOWNLOAD_URL));
    }

    @Test
    void baseCharacterSheetFrom_sheetWithoutSystem_returnsNullSystem() {
        CharacterSheet sheet = sheet();
        sheet.setGameSystem(null);

        BaseCharacterSheetDTO dto = BaseCharacterSheetDTO.from(sheet, URL, DOWNLOAD_URL);

        assertThat(dto.system()).isNull();
    }

    @Test
    void baseCharacterSheetFrom_nullUrls_keepsThemNull() {
        BaseCharacterSheetDTO dto = BaseCharacterSheetDTO.from(sheet(), null, null);

        assertThat(dto).extracting(BaseCharacterSheetDTO::url, BaseCharacterSheetDTO::downloadUrl).containsOnlyNulls();
    }

    @Test
    void baseCharacterSheetFrom_sheet_doesNotExposeStorageKeys() {
        BaseCharacterSheetDTO dto = BaseCharacterSheetDTO.from(sheet(), URL, DOWNLOAD_URL);

        assertThat(dto.toString()).doesNotContain("secret-key", "diary-documents");
    }

    @Test
    void detailCharacterSheetFrom_sheet_copiesAllFieldsAndUrls() {
        DetailCharacterSheetDTO dto = DetailCharacterSheetDTO.from(sheet(), URL, DOWNLOAD_URL);

        assertThat(dto).isEqualTo(new DetailCharacterSheetDTO(SHEET_ID, CHARACTER_ID, "Alira, level 9", "D&D 5e", "alira.pdf",
                2048L, SHEET_CREATED_AT, URL, DOWNLOAD_URL));
    }

    @Test
    void detailCharacterSheetFrom_sheetWithoutSystem_returnsNullSystem() {
        CharacterSheet sheet = sheet();
        sheet.setGameSystem(null);

        DetailCharacterSheetDTO dto = DetailCharacterSheetDTO.from(sheet, URL, DOWNLOAD_URL);

        assertThat(dto.system()).isNull();
    }

    @Test
    void detailCharacterSheetFrom_sheet_doesNotExposeStorageKeys() {
        DetailCharacterSheetDTO dto = DetailCharacterSheetDTO.from(sheet(), URL, DOWNLOAD_URL);

        assertThat(dto.toString()).doesNotContain("secret-key", "diary-documents");
    }
}
