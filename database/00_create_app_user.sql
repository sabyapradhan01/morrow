-- Run as an Oracle administrator connected to your Oracle XE 11g database (service/SID XE).
-- Replace the example password before execution. Do not use SYS/SYSTEM from the web application.
CREATE USER MORROW_APP IDENTIFIED BY "Replace_With_A_Strong_Password_123";
GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE SEQUENCE, CREATE TRIGGER TO MORROW_APP;
ALTER USER MORROW_APP QUOTA 100M ON USERS;

-- If your database uses a tablespace other than USERS, change the quota statement above.
