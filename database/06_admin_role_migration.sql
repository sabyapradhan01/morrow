-- Morrow admin/user role migration for an EXISTING Oracle XE 11g database.
-- Run as MORROW_APP, after backing up the schema.
-- It preserves existing accounts and defaults them to USER.

SET SERVEROUTPUT ON
SET DEFINE ON

DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count
      FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'USERS' AND COLUMN_NAME = 'ROLE';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE USERS ADD (ROLE VARCHAR2(10) DEFAULT ''USER'' NOT NULL)';
        DBMS_OUTPUT.PUT_LINE('Added USERS.ROLE. Existing accounts defaulted to USER.');
    ELSE
        DBMS_OUTPUT.PUT_LINE('USERS.ROLE already exists; keeping current roles.');
    END IF;

    SELECT COUNT(*) INTO v_count
      FROM USER_CONSTRAINTS
     WHERE TABLE_NAME = 'USERS' AND CONSTRAINT_NAME = 'CK_USERS_ROLE';

    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE USERS ADD CONSTRAINT CK_USERS_ROLE CHECK (ROLE IN (''USER'', ''ADMIN''))';
        DBMS_OUTPUT.PUT_LINE('Added CK_USERS_ROLE constraint.');
    END IF;
END;
/

PROMPT Existing accounts are USER by default. Promote ONE trusted account to ADMIN:
ACCEPT admin_email CHAR PROMPT 'Email address to promote to ADMIN: '
UPDATE USERS SET ROLE = 'ADMIN'
 WHERE LOWER(EMAIL) = LOWER(TRIM('&admin_email')) AND STATUS = 'ACTIVE';

PROMPT If 0 rows updated, check the email and rerun this promotion section.
COMMIT;

PROMPT Current role assignments:
SELECT USER_ID, FULL_NAME, EMAIL, ROLE, STATUS FROM USERS ORDER BY USER_ID;
