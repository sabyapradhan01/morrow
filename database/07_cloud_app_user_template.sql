-- TEMPLATE for Oracle Autonomous Database only.
-- Run as the cloud database ADMIN user. Replace the password locally before executing.
-- Never commit your replaced password to GitHub.
CREATE USER MORROW_APP IDENTIFIED BY "REPLACE_WITH_A_UNIQUE_STRONG_PASSWORD";
GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE SEQUENCE, CREATE TRIGGER TO MORROW_APP;
GRANT UNLIMITED TABLESPACE TO MORROW_APP;
