# Morrow — Digital Wallet Management System (Cloud Edition)

Morrow is an academic digital-wallet simulation built with Java 21, Spring Boot, Thymeleaf, HTML/CSS/JavaScript, Spring JDBC (`JdbcTemplate`), the PostgreSQL JDBC driver (pgJDBC), and a hosted Neon PostgreSQL database. It is not a financial service and does not connect to banks. All funds/top-ups/transfers are simulated.

> **Keep the local Oracle PAT copy separate.** This is a cloud edition that uses PostgreSQL/Neon while retaining JDBC. It does not connect to `localhost:1521:XE` and does not automatically migrate records from Oracle XE.

## Features

- User registration and login with BCrypt password hashes and server-side sessions.
- Individual INR wallets, simulated top-ups, transfers, transaction history, and a wallet ledger.
- Separate ADMIN and USER roles checked by the backend.
- Admin pages to manage users, wallets, transactions, and ledger entries.
- JDBC persistence using `JdbcTemplate` and Spring transaction management.
- Docker configuration and Render Blueprint for deployment.
- Hosted raw SQL workbench is **disabled by default**. Use the dedicated admin pages for supported management actions. For ad-hoc SQL, use Neon SQL Editor. The unrestricted workbench is dangerous on a public service and should only be enabled in a trusted local environment.

## Tables

| Table | Purpose |
|---|---|
| `users` | Account data, status, role, and BCrypt password hash |
| `wallets` | Wallet owner, currency, and balance |
| `transactions` | Simulated top-ups and transfers |
| `ledger` | Credits/debits and balance-after values |

## A. Create the Neon database

1. Create a free PostgreSQL project at [Neon](https://neon.tech/). Select the free plan; do not create paid compute/resources.
2. In the Neon dashboard, open **SQL Editor**.
3. Open `database/01_schema_postgresql.sql` from this repository, copy the whole file into Neon SQL Editor, and run it once against the new, empty database.
4. Run `database/03_diagnostic_queries_postgresql.sql` to verify the four tables were created.
5. In Neon’s **Connect** dialog, choose the JDBC connection details. Record the host, database name, role/user, and password privately. Use TLS.

The JDBC URL shape is:

```text
jdbc:postgresql://YOUR_NEON_HOST/YOUR_DATABASE?sslmode=require
```

Use the actual hostname and database shown by Neon. Set username/password separately via environment variables. Do not put database credentials in source code, a committed `.env` file, or a public README.

## B. Deploy from GitHub to Render

1. Push this cloud edition to your `morrow` GitHub repository (or to a separate deployment branch if you want to preserve the Oracle project on GitHub).
2. Open [Render](https://render.com/) and connect GitHub.
3. Choose **New → Blueprint**, select the `morrow` repository/branch that contains `render.yaml`, and review the free web service definition.
4. Fill the three secret variables when prompted, or add them from the Render service’s **Environment** page:
   - `NEON_JDBC_URL`: JDBC URL from Neon; include `sslmode=require`.
   - `NEON_DB_USER`: Neon database role/user.
   - `NEON_DB_PASSWORD`: Neon database password.
5. Keep `SESSION_COOKIE_SECURE=true` and `MORROW_ADMIN_SQL_ENABLED=false` on the public deployment.
6. Deploy and inspect Render's build/runtime logs. The app uses Render's `PORT` automatically.
7. Test `https://YOUR-RENDER-URL/health` and `https://YOUR-RENDER-URL/health/db`.

Free Render services may spin down when idle, causing the first request to take roughly a minute to wake. Neon’s free compute can also scale to zero. Free-tier limits and availability may change; confirm the current terms in each dashboard. Do not select a paid service/compute plan.

## C. Bootstrap the first administrator

The database schema defaults all registered accounts to `USER`. This prevents public registration from giving someone admin access automatically.

1. Open the deployed Morrow URL and register the account you intend to use as the first admin.
2. In Neon SQL Editor, edit the email in `database/02_bootstrap_admin_postgresql.sql` to that exact registered email, then run it once.
3. Verify the result. The chosen active account should show `ADMIN`; other accounts should remain `USER`.
4. Sign out of Morrow, then open `https://YOUR-RENDER-URL/admin` and sign in using that account’s existing email/password.

Example manual statement (replace the email):

```sql
UPDATE users
SET role = 'ADMIN'
WHERE LOWER(email) = LOWER('your-admin-email@example.com')
  AND status = 'ACTIVE';
```

Expected output is `UPDATE 1`. If it returns `UPDATE 0`, check the email and status. Do not run `01_schema_postgresql.sql` again against a populated database.

## D. Run locally against Neon (optional)

Install JDK 21 and Maven 3.9+. In PowerShell at the project root, set the connection environment variables for that terminal session:

```powershell
$env:NEON_JDBC_URL = 'jdbc:postgresql://YOUR_NEON_HOST/YOUR_DATABASE?sslmode=require'
$env:NEON_DB_USER = 'YOUR_NEON_ROLE'
$env:NEON_DB_PASSWORD = 'YOUR_NEON_PASSWORD'
$env:SESSION_COOKIE_SECURE = 'false'
$env:MORROW_ADMIN_SQL_ENABLED = 'true'  # only for a trusted local environment; not public hosting
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

Open `http://localhost:8081`. When running behind HTTPS on Render, keep `SESSION_COOKIE_SECURE=true`.

## E. SQL diagnostics

Run the diagnostic SQL in `database/03_diagnostic_queries_postgresql.sql` from Neon SQL Editor to review users, balances, transactions, ledger rows, and table names. Never change a wallet balance by itself during a demo; use Morrow’s transaction logic so the transaction and ledger remain consistent.

## Security boundaries

- Never commit secrets. If a secret was previously pushed to GitHub, rotate it.
- The public hosted SQL workbench is disabled by default. Do not set `MORROW_ADMIN_SQL_ENABLED=true` on the public Render service because arbitrary DDL/DML may destroy the database.
- Admin endpoints re-check role/status server-side. Keep only trusted accounts assigned `ADMIN`.
- No real payment integration exists. Do not store real bank/card details or use real funds.

## Architecture

Browser (HTML/CSS/JS + Thymeleaf) → Spring MVC controller → service layer → `JdbcTemplate` / pgJDBC → Neon PostgreSQL. Successful changes are persisted in PostgreSQL and returned to the browser as JSON. Admins can inspect/edit supported records using the control room, and verify them in the Neon SQL Editor.

## GitHub Actions

`.github/workflows/build.yml` compiles and runs the Spring test suite on pushes and pull requests to `main` or `neon-deploy`. The CI variables are placeholders and do not contain production credentials.
