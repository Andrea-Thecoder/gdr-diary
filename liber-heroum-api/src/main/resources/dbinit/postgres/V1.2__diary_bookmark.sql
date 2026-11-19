alter table diary add column bookmark_page_id uuid;

create index ix_diary_bookmark_page_id on diary (bookmark_page_id);
alter table diary add constraint fk_diary_bookmark_page_id foreign key (bookmark_page_id) references diary_page (id) on delete set null on update restrict;
