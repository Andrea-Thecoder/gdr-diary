create table character_sheet (
  id                            uuid not null,
  character_id                  uuid not null,
  document_id                   uuid not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  title                         varchar(100) not null,
  game_system                   varchar(100),
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint uq_character_sheet_document_id unique (document_id),
  constraint pk_character_sheet primary key (id)
);

create index ix_character_sheet_character_id on character_sheet (character_id);
alter table character_sheet add constraint fk_character_sheet_character_id foreign key (character_id) references game_character (id) on delete restrict on update restrict;

alter table character_sheet add constraint fk_character_sheet_document_id foreign key (document_id) references document (id) on delete restrict on update restrict;

create index if not exists ix_character_sheet_title on character_sheet (title);
create index if not exists ix_character_sheet_game_system on character_sheet (game_system);
