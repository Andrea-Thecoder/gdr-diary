create table payment (
  id                            uuid not null,
  app_user_id                   uuid,
  provider                      varchar(30) not null,
  provider_payment_id           varchar(255) not null,
  status                        varchar(20) not null,
  amount_cents                  bigint not null,
  currency                      varchar(3) not null,
  description                   varchar(255) not null,
  paid_at                       timestamptz,
  customer_email                varchar(254) not null,
  customer_name                 varchar(255),
  customer_tax_code             varchar(32),
  customer_country              varchar(2),
  invoice_number                varchar(64),
  receipt_url                   varchar(2048),
  retain_until                  date not null,
  created_at                    timestamptz not null,
  updated_at                    timestamptz not null,
  version                       bigint not null,
  created_by                    varchar not null,
  updated_by                    varchar not null,
  constraint ck_payment_provider check (provider in ('STRIPE')),
  constraint ck_payment_status check (status in ('PENDING','SUCCEEDED','FAILED','REFUNDED')),
  constraint ck_payment_amount_cents check (amount_cents >= 0),
  constraint ck_payment_currency check (currency ~ '^[A-Z]{3}$'),
  constraint ck_payment_customer_country check (customer_country is null or customer_country ~ '^[A-Z]{2}$'),
  constraint uq_payment_provider_payment_id unique (provider_payment_id),
  constraint pk_payment primary key (id)
);

create index ix_payment_app_user_id_paid_at on payment (app_user_id, paid_at);
alter table payment add constraint fk_payment_app_user_id foreign key (app_user_id) references app_user (id) on delete set null on update restrict;
