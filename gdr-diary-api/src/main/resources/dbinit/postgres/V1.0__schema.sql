-- apply changes
create table app_user (
  id                            uuid not null,
  is_premium                    boolean default false not null,
  avatar_document_id            uuid,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  keycloak_id                   varchar(36) not null,
  email                         varchar(255),
  settings                      jsonb,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint uq_app_user_keycloak_id unique (keycloak_id),
  constraint pk_app_user primary key (id)
);

create table diary (
  id                            uuid not null,
  font_size                     smallint,
  character_id                  uuid not null,
  cover_document_id             uuid,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  title                         varchar(255) not null,
  description                   varchar(2000),
  cover_color                   varchar(7),
  font                          varchar(100),
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint pk_diary primary key (id)
);

create table diary_page (
  id                            uuid not null,
  page_number                   smallint not null,
  diary_id                      uuid not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  content                       jsonb not null,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint uq_diary_page_diary_id_page_number unique (diary_id,page_number),
  constraint pk_diary_page primary key (id)
);

create table document (
  id                            uuid not null,
  size                          bigint not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  name                          varchar(255) not null,
  mime_type                     varchar(127) not null,
  bucket                        varchar(63) not null,
  object_key                    varchar(1024) not null,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint pk_document primary key (id)
);

create table game_character (
  id                            uuid not null,
  owner_id                      uuid not null,
  avatar_document_id            uuid,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  name                          varchar(255) not null,
  description                   varchar(2000),
  campaign_name                 varchar(255),
  background_color              varchar(7),
  kind                          varchar(20) not null,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint ck_game_character_kind check ( kind in ('PLAYER','GAME_MASTER')),
  constraint pk_game_character primary key (id)
);

-- foreign keys and indices
create index ix_app_user_avatar_document_id on app_user (avatar_document_id);
alter table app_user add constraint fk_app_user_avatar_document_id foreign key (avatar_document_id) references document (id) on delete set null on update restrict;

create index ix_diary_character_id on diary (character_id);
alter table diary add constraint fk_diary_character_id foreign key (character_id) references game_character (id) on delete restrict on update restrict;

create index ix_diary_cover_document_id on diary (cover_document_id);
alter table diary add constraint fk_diary_cover_document_id foreign key (cover_document_id) references document (id) on delete set null on update restrict;

create index ix_diary_page_diary_id on diary_page (diary_id);
alter table diary_page add constraint fk_diary_page_diary_id foreign key (diary_id) references diary (id) on delete restrict on update restrict;

create index ix_game_character_owner_id on game_character (owner_id);
alter table game_character add constraint fk_game_character_owner_id foreign key (owner_id) references app_user (id) on delete restrict on update restrict;

create index ix_game_character_avatar_document_id on game_character (avatar_document_id);
alter table game_character add constraint fk_game_character_avatar_document_id foreign key (avatar_document_id) references document (id) on delete set null on update restrict;

create index if not exists ix_app_user_email on app_user (email);
create index if not exists ix_diary_title on diary (title);
create index if not exists ix_document_bucket_object_key on document (bucket,object_key);
create index if not exists ix_document_name on document (name);
create index if not exists ix_game_character_name on game_character (name);
create index if not exists ix_game_character_campaign_name on game_character (campaign_name);
