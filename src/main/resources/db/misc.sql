truncate table account cascade;

INSERT INTO account (id, balance, name, type) VALUES (1, 500.00, 'alice', 'asset');
INSERT INTO account (id, balance, name, type) VALUES (2, 500.00, 'alice', 'expense');
INSERT INTO account (id, balance, name, type) VALUES (3, 500.00, 'bob', 'asset');
INSERT INTO account (id, balance, name, type) VALUES (4, 500.00, 'bob', 'expense');
