create table feedback (
  id                            uuid not null,
  type                          varchar(20) not null,
  status                        varchar(20) not null,
  email                         varchar(254) not null,
  privacy_accepted              boolean not null,
  message                       text not null,
  technical_info                jsonb,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint ck_feedback_type check (type in ('IDEA','BUG','LOVE')),
  constraint ck_feedback_status check (status in ('NEW','READ','ANSWERED','ARCHIVED')),
  constraint ck_feedback_privacy_accepted check (privacy_accepted = true),
  constraint ck_feedback_message_length check (char_length(message) between 1 and 2000),
  constraint pk_feedback primary key (id)
);

create table feedback_document (
  id                            uuid not null,
  feedback_id                   uuid not null,
  document_id                   uuid not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint uq_feedback_document_document_id unique (document_id),
  constraint pk_feedback_document primary key (id)
);

create index ix_feedback_document_feedback_id on feedback_document (feedback_id);
alter table feedback_document add constraint fk_feedback_document_feedback_id foreign key (feedback_id) references feedback (id) on delete restrict on update restrict;

alter table feedback_document add constraint fk_feedback_document_document_id foreign key (document_id) references document (id) on delete restrict on update restrict;

create index if not exists ix_feedback_type on feedback (type);
create index if not exists ix_feedback_status on feedback (status);
create index if not exists ix_feedback_email on feedback (email);
create index if not exists ix_feedback_created_at on feedback (created_at);
