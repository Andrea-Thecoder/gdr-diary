create table diary_page_tag (
  id                            uuid not null,
  page_id                       uuid not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  name                          varchar(30) not null,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint pk_diary_page_tag primary key (id)
);

alter table diary_page_tag add constraint fk_diary_page_tag_page_id foreign key (page_id) references diary_page (id) on delete restrict on update restrict;

create unique index uq_diary_page_tag_page_id_lower_name on diary_page_tag (page_id, lower(name));

create extension if not exists pg_trgm;

create index ix_diary_page_tag_name_trgm on diary_page_tag using gin (name gin_trgm_ops);
