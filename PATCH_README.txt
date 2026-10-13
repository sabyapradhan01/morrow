MORROW ADMIN ROLE PATCH

Recommended: back up the entire working project first.

1. Stop Spring Boot with Ctrl+C.
2. Merge this patch into the root of your existing Morrow-Wallet-JDBC-Phase1 folder, preserving relative paths and allowing listed files to overwrite.
3. Do NOT run database/01_schema.sql against your existing populated database.
4. Open SQL*Plus, connect as MORROW_APP@XE, and run:
      @database/06_admin_role_migration.sql
   (Use the script's full path if SQL*Plus cannot find it.) Enter the email of one existing ACTIVE account to promote to ADMIN. That account keeps its existing website password. Existing accounts default to USER.
5. Verify with:
      SELECT USER_ID, FULL_NAME, EMAIL, ROLE, STATUS FROM USERS ORDER BY USER_ID;
6. Start Spring Boot with the usual Oracle environment variables:
      mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
7. User login: http://localhost:8081
   Admin login: http://localhost:8081/admin

The admin SQL workbench is for local academic use only. DDL may commit implicitly. Back up before destructive work. Full setup and demo notes are in ADMIN_SETUP_AND_DEMO.md.
