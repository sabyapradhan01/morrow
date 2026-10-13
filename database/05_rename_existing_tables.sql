-- Morrow table-name migration for an EXISTING Oracle XE 11g database.
-- Run this script ONCE as MORROW_APP after stopping the Spring Boot application.
-- It renames tables in place; existing rows, IDs, balances, foreign keys,
-- indexes and attached triggers are retained. Do NOT run 01_schema.sql first.
--
-- Expected old names: MW_USERS, MW_WALLETS, MW_TRANSACTIONS, MW_WALLET_LEDGER
-- New names:          USERS, WALLETS, TRANSACTIONS, LEDGER

SET ECHO ON

RENAME MW_USERS TO USERS;
RENAME MW_WALLETS TO WALLETS;
RENAME MW_TRANSACTIONS TO TRANSACTIONS;
RENAME MW_WALLET_LEDGER TO LEDGER;

PROMPT Table rename complete. Verify with:
PROMPT SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME IN ('USERS','WALLETS','TRANSACTIONS','LEDGER') ORDER BY TABLE_NAME;
