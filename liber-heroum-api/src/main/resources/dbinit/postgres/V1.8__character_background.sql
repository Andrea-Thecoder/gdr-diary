-- La storia del personaggio: un solo documento per personaggio, in una tabella a parte per non appesantire le letture del personaggio.
-- La chiave esterna e' a cascata: eliminando il personaggio (o l'account, che elimina i personaggi) sparisce anche la sua storia.
create table character_background (
  id                            uuid not null,
  character_id                  uuid not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  content                       jsonb not null,
  preview                       varchar(300) not null,
  text_length                   integer not null,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint uq_character_background_character_id unique (character_id),
  constraint ck_character_background_text_length check (text_length >= 0),
  constraint pk_character_background primary key (id)
);

alter table character_background add constraint fk_character_background_character_id foreign key (character_id) references game_character (id) on delete cascade on update restrict;
