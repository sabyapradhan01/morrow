## Deployment

Deployment instructions and cloud configuration are in [`DEPLOYMENT_GUIDE.md`](DEPLOYMENT_GUIDE.md). Do not advertise a live URL until the service has deployed and `/health/db` returns `status: ok`.

The hosted safety configuration disables raw SQL execution (`MORROW_ADMIN_SQL_ENABLED=false`). Use local SQL*Plus for unrestricted classroom DDL/DML demonstrations. All wallet funds are simulated.
## What's implemented in this release

- Account registration and sign-in against `USERS`.
- Password storage with BCrypt hashes (never plaintext); normalized lowercase email addresses.
- Server-side HTTP session, session ID rotation at sign-in, HTTP-only `JSESSIONID`, `SameSite=Lax`, and a 30-minute inactivity timeout.
- Oracle-backed INR wallet created for each new user.
- Simulated top-ups persisted as wallet credits, transactions, and ledger entries.
- Transfers between registered Morrow users by email. Both wallets are locked and debited/credited inside a single database transaction; transaction and ledger writes roll back together on failure.
- Database-backed transaction history, balance and totals, filtering/search, CSV export, sign-out, and balance visibility.
- Server-side amount/email validation, generic invalid-credential messages, safe API errors, and common browser security headers.
- SQL DDL, sample SQL DML, and useful Oracle queries for the PAT submission.
- Separate user and administrator sign-in. Admin permissions are checked on the server against the role saved in Oracle.
- Admin control room at `/admin`: user create/edit/close, wallet balance adjustments recorded as transactions and ledger entries, transaction description edits/deletion with linked-ledger cleanup and balance rebuild, ledger entry edits/deletion, and an admin-only SQL workbench.

**Important project boundary:** top-ups are simulated credits for demonstrating DML; they do not verify a bank payment. The virtual card is a visual mock-up only. Do not use this project to store or move real money or expose it publicly as a production banking service.

## Technology stack

- Java 21
- Spring Boot 3.5.16 / Spring MVC / Thymeleaf
- Spring JDBC (`JdbcTemplate`) and Spring transaction management
- Oracle Database XE 11g with JDBC Thin driver (`ojdbc8` 12.2 legacy-driver line)
- Maven
- HTML, CSS and vanilla JavaScript
- BCrypt from `spring-security-crypto` for password hashing; no JPA/Hibernate, Python, or ODBC

## Important Oracle-version note

Your listener screenshot shows Oracle Database XE 11g (`XE` service/SID), not a modern pluggable database. This project variant replaces identity columns with sequences/triggers and replaces `FETCH FIRST` with Oracle 11g-compatible `ROWNUM`. Oracle 11g is an old release; the legacy driver is provided as a compatibility attempt with Java 21, but that specific driver/JDK combination is not Oracle-certified. If the driver fails at runtime, the robust long-term option is to use a newer Oracle Database release that is certified with a current JDBC driver.

## Windows prerequisites

1. Install **JDK 21**. In a new VS Code terminal verify `java -version` and `javac -version`.
2. Install Apache Maven 3.9+ and verify `mvn -version`.
3. Install VS Code's **Extension Pack for Java** (optional but recommended).
4. Start Oracle XE 11g. Your screenshot shows listener port `1521` and service/SID `XE`.

## 1. Create the Oracle application user

Connect as an Oracle administrator to Oracle XE 11g, edit the example password in `database/00_create_app_user.sql`, and run it once:

```sql
sqlplus system@XE
@database/00_create_app_user.sql
```

Do not connect the web application as `SYS` or `SYSTEM`.

## 2. Create or migrate the tables

The table names are now easier to remember:

| Table | What it stores |
|---|---|
| `USERS` | Morrow accounts and password hashes |
| `WALLETS` | Each user's wallet and current balance |
| `TRANSACTIONS` | Simulated top-ups and transfers |
| `LEDGER` | Credit/debit entries and balance-after values |

**For a brand-new database only:** connect as `MORROW_APP` to XE and run `@database/01_schema.sql` once. This creates the four simple table names, sequences, triggers, constraints and indexes.

**For your existing Morrow database that already contains accounts and wallet data:** stop Spring Boot first, connect as `MORROW_APP`, then run `@database/05_rename_existing_tables.sql` exactly once. This renames the existing tables in place; it does not delete or recreate any records. Do not run `01_schema.sql` on an already populated database, and do not run the rename script a second time.

After migration, verify the names with:

```sql
SELECT TABLE_NAME
FROM USER_TABLES
WHERE TABLE_NAME IN ('USERS', 'WALLETS', 'TRANSACTIONS', 'LEDGER')
ORDER BY TABLE_NAME;
```

Register new accounts through Morrow's **Create an account** form. Registration stores a BCrypt password hash and creates a zero-balance INR wallet in a database transaction. Do not insert a placeholder password hash manually.

### Enable administrator access on an existing database

First stop Spring Boot and make a backup of your schema/data. Connect to the **existing** schema as `MORROW_APP`, then run the one-time role migration:

```sql
@database/06_admin_role_migration.sql
```

The script adds `USERS.ROLE` if missing, defaults existing users to `USER`, and prompts you for the email of one existing active account to promote to `ADMIN`. The promoted account keeps its existing password and wallet; it signs in at `/admin`. Other accounts remain `USER` and sign in at `/`. Check the output and confirm exactly the intended account is `ADMIN`.

For a brand-new database, `01_schema.sql` already creates the `ROLE` column and role constraint; run the migration only if you need to promote an account. The script is safe to rerun for the column/constraint checks, but it promotes the email you enter each time.

**Admin control room:** open `http://localhost:8081/admin`. The admin UI calls `/api/admin/*`; each endpoint verifies the active `ADMIN` role from Oracle. The SQL workbench supports one `SELECT`/`WITH`, DML (`INSERT`, `UPDATE`, `DELETE`, `MERGE`), DDL (`CREATE`, `ALTER`, `DROP`, `TRUNCATE`, `RENAME`, `COMMENT`), and DCL (`GRANT`, `REVOKE`) statement at a time. Results are capped at 200 rows, and password hashes are blocked from the workbench. PL/SQL blocks and TCL should be run in SQL*Plus, not the web workbench. The workbench is powerful and intentionally for a local academic demo only—never expose this application publicly. It runs as `MORROW_APP`, so it cannot perform operations beyond that Oracle account's privileges (for example, creating Oracle database users requires DBA privileges and is not exposed through the web app).

### Optional SQL DML demonstration

After an account has been registered in the UI, demonstrate a simulated wallet credit directly through SQL*Plus with:

```sql
@database/02_sample_dml.sql
```

SQL*Plus prompts for `user_email` and `topup_amount`. The script updates `WALLETS`, inserts `TRANSACTIONS`, and inserts `LEDGER`, then commits. This is optional; the UI can also create top-ups using JDBC.

`database/03_useful_queries.sql` contains read/report queries. `database/04_reset_demo_data.sql` is destructive: inspect it before use and only run it on disposable classroom data.

## 3. Configure the JDBC connection

From the project root, set these values in the **same PowerShell terminal** that will launch Spring Boot. Replace the example password with the password you chose for `MORROW_APP`:

```powershell
$env:ORACLE_JDBC_URL = 'jdbc:oracle:thin:@localhost:1521:XE'
$env:ORACLE_DB_USER = "MORROW_APP"
$env:ORACLE_DB_PASSWORD = "the_password_you_set_for_MORROW_APP"
```

The defaults in `application.properties` are only examples. Do not commit your real password to Git. `ojdbc8` is resolved by Maven; JDBC Thin does not need a separate Oracle Instant Client installation.

## 4. Run Morrow

Open the extracted project root in VS Code, then run:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

Port `8081` is used to avoid the port `8080` conflict that occurred on the development machine. Open:

- `http://localhost:8081` — Morrow UI
- `http://localhost:8081/health` — application health
- `http://localhost:8081/health/db` — Oracle JDBC connectivity test

The application may start while Oracle is offline; database-backed operations will report a safe error until the DB becomes available.

## API endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Create account, BCrypt password hash, INR wallet and signed-in session |
| `POST` | `/api/auth/login` | Verify a USER credential and start a server session |
| `POST` | `/api/auth/admin-login` | Verify an ADMIN credential and start an admin session |
| `GET` | `/api/auth/me` | Return current signed-in user's wallet summary |
| `POST` | `/api/auth/logout` | Invalidate server session |
| `GET` | `/api/wallet/summary` | Return user's wallet balance and credit/debit totals |
| `GET` | `/api/wallet/transactions` | Return recent transaction history for that wallet |
| `POST` | `/api/wallet/top-up` | Record a simulated wallet credit, transaction and ledger row |
| `POST` | `/api/wallet/transfers` | Atomically transfer simulated funds to another active Morrow user |
| `GET` | `/api/admin/overview` | Admin-only live database counts and total balance |
| `GET/POST/PUT/DELETE` | `/api/admin/users` | Admin-only account management (delete means soft-close) |
| `GET/PUT` | `/api/admin/wallets` | Admin-only wallet inspection and audited balance adjustments |
| `GET/PUT/DELETE` | `/api/admin/transactions` | Admin-only transaction description edits and deletion with ledger cleanup |
| `GET/PUT/DELETE` | `/api/admin/ledger` | Admin-only ledger management |
| `POST` | `/api/admin/sql` | Admin-only single-statement SQL workbench

The wallet endpoints require a USER session; admin endpoints independently re-check the active account role from Oracle. The UI uses same-origin JSON requests. Admin SQL workbench is dangerous by design; only use it locally with disposable academic data.

## Build and test

```powershell
mvn test
mvn package
```

Maven creates `target/morrow-wallet-0.1.0-SNAPSHOT.jar`. Run it with the same Oracle environment variables configured:

```powershell
java -jar target/morrow-wallet-0.1.0-SNAPSHOT.jar --server.port=8081
```

## Try the main flow

1. Choose **Create an account** and register with a valid email and password of at least 8 characters.
2. The app creates the user and an INR wallet in Oracle. A brand-new wallet starts with ₹0.00.
3. Add a simulated top-up, e.g. ₹1,000. The balance, transaction and ledger row are committed to Oracle.
4. Open a second browser/incognito window and register a second account.
5. Copy the second account's email into the first account's **Transfer** form, enter an amount and confirm. Both wallet balances and the debit/credit ledger entries are updated atomically.
6. Sign out, sign back in, or restart the application. The account and transaction data remain in Oracle.

## Troubleshooting

- **`/health/db` returns `unavailable`:** check Oracle XE is running, the `XE` SID/port is right, and `MORROW_APP` can sign in.
- **`ORA-01017`:** the app username/password is wrong or `MORROW_APP` was created in another PDB.
- **`ORA-12514` / `ORA-12505`:** use `XE` with the SID URL `jdbc:oracle:thin:@localhost:1521:XE`.
- **`ORA-00942` when registering or topping up:** connect as `MORROW_APP` to the correct PDB and run `database/01_schema.sql` if the tables were not created.
- **Port `8080` is already in use:** keep launching with `--server.port=8081`. Do not stop Oracle Listener just to free a web-server port.
- **Email already exists:** sign in using that account, or register with a different email. Email is normalized to lowercase. If you ran an older version of `database/02_sample_dml.sql`, it may have left a `demo@morrow.local` row with a placeholder hash; inspect `USERS` in SQL*Plus and remove that obsolete placeholder row before trying to register the same email, or simply register with a different email.
- **Maven cannot resolve dependencies:** check internet/proxy settings, then try `mvn -U clean package`.

## Known limitations

This is a classroom simulation, not production banking software. It does not integrate with payment rails, banks, OTP/email verification, password recovery, bank-issued cards, or money requests. Production deployment would also need HTTPS, robust rate limiting, stronger audit/monitoring procedures, and a security review.

## Frontend visual refresh

The latest frontend styles apply a warm paper-and-ink editorial direction inspired by the supplied concept reference: subtle paper grain and drafting grids, architectural linework in the balance panel, muted olive accents, squared-off components, and a responsive layout. This update is CSS-only; it leaves the existing HTML IDs, Thymeleaf bindings, JavaScript and backend endpoints unchanged.
