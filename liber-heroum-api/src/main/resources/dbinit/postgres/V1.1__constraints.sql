alter table diary_page add constraint ck_diary_page_page_number check (page_number between 1 and 100);

alter table diary_page drop constraint if exists uq_diary_page_diary_id_page_number;
alter table diary_page add constraint uq_diary_page_diary_id_page_number unique (diary_id, page_number) deferrable initially deferred;

alter table game_character add constraint ck_game_character_background_color check (background_color ~ '^#[0-9A-Fa-f]{6}$');

alter table diary add constraint ck_diary_cover_color check (cover_color ~ '^#[0-9A-Fa-f]{6}$');
