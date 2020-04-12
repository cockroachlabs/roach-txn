-- drop table if exists account cascade;
-- drop table if exists databasechangelog cascade;
-- drop table if exists databasechangeloglock cascade;

create table account
(
    id      bigint         not null,
    balance numeric(19, 2) not null,
    name    varchar(128)   not null,
    type    varchar(25)    not null,

    primary key (id)
);

create unique index idx_account on account (name,type);
