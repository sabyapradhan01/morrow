# Morrow Admin Setup and Demo

## Before starting the updated application

1. Stop Spring Boot (`Ctrl+C`). Back up the Oracle schema/data.
2. Open SQL*Plus / Run SQL Command Line and connect as the app schema: `CONNECT MORROW_APP@XE`.
3. Run `@database/06_admin_role_migration.sql` from the project root (or use its full path). Enter the email of an existing **active account** to promote to `ADMIN`.
4. Confirm with `SELECT USER_ID, FULL_NAME, EMAIL, ROLE, STATUS FROM USERS ORDER BY USER_ID;`.
5. If your database user does not have enough privileges for the app schema, correct the grants as a DBA; do not connect the web application as SYSTEM.
6. Set the JDBC environment variables in the same PowerShell session and start Spring Boot with the usual Maven command.

The promoted account keeps its existing password and wallet. The admin account signs in at `http://localhost:8081/admin`. An account with role `USER` signs in at `http://localhost:8081`. New self-registered accounts are always `USER`.

## Admin control room capabilities

- Overview: counts for users, wallets, transactions, ledger entries and sum of wallet balances.
- Users: create users (BCrypt hash plus INR wallet), edit name/email/role/status, optionally reset password, or soft-close an account. Closing preserves foreign keys and finance history.
- Wallets: view balances and set a target balance. The change is recorded as a simulated transaction and ledger entry.
- Transactions: edit descriptions or delete a transaction. Deletion removes its associated ledger rows and recalculates affected wallet balances from remaining ledger entries.
- Ledger: edit or delete a ledger row. This is an accounting-level action and can cause a mismatch with the associated transaction; use for academic demonstration with test rows only.
- SQL workbench: run one statement at a time. SELECT/WITH results are capped at 200 rows. DML, DDL and DCL are supported when `MORROW_APP` has the needed privileges. `PASSWORD_HASH` is blocked from SQL workbench queries. PL/SQL blocks and TCL commands such as COMMIT/ROLLBACK should be demonstrated in SQL*Plus, not through the web workbench.

## Live demo sequence

1. Sign in as admin at `/admin` and show the overview.
2. Open Users and create a disposable test user; verify a new `USERS` row and matching `WALLETS` row.
3. Open SQL Workbench, run `SELECT USER_ID, FULL_NAME, EMAIL, ROLE, STATUS FROM USERS ORDER BY USER_ID`.
4. Set a test wallet balance through Wallets; then inspect `TRANSACTIONS` and `LEDGER`.
5. Make a regular user-to-user transfer in the user site. Refresh the admin Transactions and Ledger pages or run the equivalent SELECTs in SQL Workbench.
6. Use only disposable records for delete demonstrations. Never delete important data as part of the demo.

## Warnings

- The admin SQL workbench can execute destructive DDL/DML, including DROP/TRUNCATE/DELETE. Do not expose the app to a public network.
- Oracle DDL may commit implicitly; rollback is not a safety net for DDL.
- The web SQL workbench uses the application DB account `MORROW_APP`, not SYSTEM. Some DCL/administrative commands may be rejected due to Oracle grants.
- Back up the database before testing edits/deletions. All funds in Morrow are simulated.
