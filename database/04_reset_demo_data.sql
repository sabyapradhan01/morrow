-- DANGER: destructive reset of demo data only. Back up first. Never run against real data.
-- Deletes ledger and transactions before wallets and users because of foreign-key relationships.
DELETE FROM LEDGER;
DELETE FROM TRANSACTIONS;
DELETE FROM WALLETS;
DELETE FROM USERS;
COMMIT;
