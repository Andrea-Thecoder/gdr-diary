-- Un solo tag per pagina, al massimo 10 tag diversi per diario (il limite dei 10 e' controllato dal service).
-- La funzione non e' ancora in produzione: la tabella della V1.4 (piu' tag per pagina) viene rifatta.
drop table diary_page_tag;

create table diary_page_tag (
  id                            uuid not null,
  diary_id                      uuid not null,
  page_id                       uuid not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  name                          varchar(30) not null,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint pk_diary_page_tag primary key (id)
);

-- Serve alla chiave esterna composta: il diario del tag e' sempre il diario della sua pagina.
alter table diary_page add constraint uq_diary_page_id_diary_id unique (id, diary_id);

alter table diary_page_tag add constraint fk_diary_page_tag_diary_id foreign key (diary_id) references diary (id) on delete restrict on update restrict;
alter table diary_page_tag add constraint fk_diary_page_tag_page_id_diary_id foreign key (page_id, diary_id) references diary_page (id, diary_id) on delete restrict on update restrict;

create unique index uq_diary_page_tag_page_id on diary_page_tag (page_id);
create index ix_diary_page_tag_diary_id_lower_name on diary_page_tag (diary_id, lower(name));
create index ix_diary_page_tag_name_trgm on diary_page_tag using gin (name gin_trgm_ops);
