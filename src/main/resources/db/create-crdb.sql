-- DROP TABLE IF EXISTS account cascade;
-- DROP TABLE IF EXISTS databasechangelog cascade;
-- DROP TABLE IF EXISTS databasechangeloglock cascade;

CREATE TABLE account
(
    id      BIGINT         NOT NULL PRIMARY KEY DEFAULT unique_rowid(),
    balance NUMERIC(19, 2) NOT NULL,
    name    VARCHAR(128)   NOT NULL,
    type    VARCHAR(25)    NOT NULL
);

create unique index idx_account ON account (name,type);
