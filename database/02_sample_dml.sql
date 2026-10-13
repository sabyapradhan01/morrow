-- Morrow Wallet DML example (Oracle SQL*Plus).
-- First create an account through the Morrow UI, then run this script as MORROW_APP
-- only if you want to demonstrate a simulated top-up performed directly in SQL.
-- This script prompts for the registered email and the top-up amount.
-- It updates the wallet balance, inserts the transaction, and inserts its ledger credit.
-- No bank is contacted and the operation is only a classroom data example.

SET SERVEROUTPUT ON

DECLARE
    l_email         USERS.EMAIL%TYPE := LOWER(TRIM('&user_email'));
    l_amount        NUMBER(14,2) := &topup_amount;
    l_user_id       USERS.USER_ID%TYPE;
    l_wallet_id     WALLETS.WALLET_ID%TYPE;
    l_new_balance   WALLETS.BALANCE%TYPE;
    l_reference     TRANSACTIONS.REFERENCE_CODE%TYPE;
    l_transaction_id TRANSACTIONS.TRANSACTION_ID%TYPE;
BEGIN
    IF l_amount < 1 OR l_amount > 1000000 THEN
        RAISE_APPLICATION_ERROR(-20001, 'Top-up must be between 1 and 1000000.');
    END IF;

    SELECT USER_ID INTO l_user_id
      FROM USERS
     WHERE LOWER(EMAIL) = l_email AND STATUS = 'ACTIVE';

    SELECT WALLET_ID INTO l_wallet_id
      FROM WALLETS
     WHERE USER_ID = l_user_id AND CURRENCY_CODE = 'INR'
     FOR UPDATE;

    UPDATE WALLETS
       SET BALANCE = BALANCE + l_amount
     WHERE WALLET_ID = l_wallet_id
     RETURNING BALANCE INTO l_new_balance;

    l_reference := 'MRW-' || SUBSTR(RAWTOHEX(SYS_GUID()), 1, 32);

    INSERT INTO TRANSACTIONS
        (REFERENCE_CODE, TRANSACTION_TYPE, SENDER_WALLET_ID,
         RECEIVER_WALLET_ID, AMOUNT, STATUS, DESCRIPTION)
    VALUES
        (l_reference, 'TOP_UP', NULL, l_wallet_id, l_amount,
         'COMPLETED', 'SQL*Plus simulated top-up example')
    RETURNING TRANSACTION_ID INTO l_transaction_id;

    INSERT INTO LEDGER
        (WALLET_ID, TRANSACTION_ID, ENTRY_TYPE, AMOUNT, BALANCE_AFTER)
    VALUES
        (l_wallet_id, l_transaction_id, 'CREDIT', l_amount, l_new_balance);

    COMMIT;
    DBMS_OUTPUT.PUT_LINE('DML complete. Reference: ' || l_reference);
    DBMS_OUTPUT.PUT_LINE('Updated wallet balance: ' || TO_CHAR(l_new_balance, 'FM9999999999990.00'));
EXCEPTION
    WHEN NO_DATA_FOUND THEN
        ROLLBACK;
        RAISE_APPLICATION_ERROR(-20002, 'No active Morrow account/wallet was found for that email.');
    WHEN OTHERS THEN
        ROLLBACK;
        RAISE;
END;
/
