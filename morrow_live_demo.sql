-- MORROW LIVE DATABASE DEMO
-- Run in SQL*Plus after connecting as MORROW_APP:
--   CONNECT MORROW_APP@XE
--   @morrow_live_demo.sql
-- This script only reads data. Run it again after each website action
-- to display the latest committed values.

SET LINESIZE 220
SET PAGESIZE 100
SET WRAP OFF
SET TAB OFF
SET TRIMSPOOL ON
SET FEEDBACK ON
SET VERIFY OFF
SET NULL '(null)'
SET COLSEP ' | '

COLUMN FULL_NAME FORMAT A24
COLUMN EMAIL FORMAT A34
COLUMN STATUS FORMAT A10
COLUMN BALANCE FORMAT 999,999,990.00
COLUMN TRANSACTION_TYPE FORMAT A16
COLUMN SENDER_WALLET_ID FORMAT 9999999999
COLUMN RECEIVER_WALLET_ID FORMAT 9999999999
COLUMN AMOUNT FORMAT 999,999,990.00
COLUMN DESCRIPTION FORMAT A30
COLUMN ENTRY_TYPE FORMAT A10
COLUMN BALANCE_AFTER FORMAT 999,999,990.00
COLUMN CREATED_AT FORMAT A19

PROMPT
PROMPT ============================================================
PROMPT 1. USERS - REGISTERED WEBSITE ACCOUNTS
PROMPT ============================================================
SELECT USER_ID, FULL_NAME, EMAIL, STATUS
FROM USERS
ORDER BY USER_ID;

PROMPT
PROMPT ============================================================
PROMPT 2. WALLETS - CURRENT BALANCES
PROMPT ============================================================
SELECT WALLET_ID, USER_ID, BALANCE
FROM WALLETS
ORDER BY WALLET_ID;

PROMPT
PROMPT ============================================================
PROMPT 3. TRANSACTIONS - TOP-UPS AND TRANSFERS
PROMPT ============================================================
SELECT TRANSACTION_ID,
       TRANSACTION_TYPE,
       SENDER_WALLET_ID,
       RECEIVER_WALLET_ID,
       AMOUNT,
       STATUS,
       TO_CHAR(CREATED_AT, 'YYYY-MM-DD HH24:MI:SS') AS CREATED_AT
FROM TRANSACTIONS
ORDER BY TRANSACTION_ID DESC;

PROMPT
PROMPT ============================================================
PROMPT 4. LEDGER - WALLET CREDITS AND DEBITS
PROMPT ============================================================
SELECT LEDGER_ID,
       TRANSACTION_ID,
       WALLET_ID,
       ENTRY_TYPE,
       AMOUNT,
       BALANCE_AFTER,
       TO_CHAR(CREATED_AT, 'YYYY-MM-DD HH24:MI:SS') AS CREATED_AT
FROM LEDGER
ORDER BY LEDGER_ID DESC;

PROMPT
PROMPT ============================================================
PROMPT SNAPSHOT COMPLETE. Run @morrow_live_demo.sql again after
PROMPT a website operation to show the latest committed database state.
PROMPT ============================================================
