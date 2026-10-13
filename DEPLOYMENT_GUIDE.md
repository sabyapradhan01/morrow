# Morrow live deployment (GitHub + Render + Oracle Autonomous Database)

## Important architecture note

GitHub stores the repository; it does not run this Spring Boot Java backend. GitHub Pages only serves static files and is not suitable for this whole application. The deployed web service must connect to an Oracle database reachable over the internet. Your local `jdbc:oracle:thin:@localhost:1521:XE` database is only accessible on your own machine, so it cannot be used as the cloud database URL.

This deployment uses:
- GitHub for source control
- Render Docker Web Service for the Java 21 Spring Boot app
- Oracle Autonomous Database (Always Free where available) for Oracle storage

The cloud database is separate from your local XE database. It starts with a schema you create there; local accounts and balances are not automatically copied. Use test accounts and simulated funds only.

## Safety defaults

`MORROW_ADMIN_SQL_ENABLED=false` is set for the hosted demo. This hides the raw SQL workbench and blocks its API while keeping regular admin screens available. Unrestricted SQL execution is too dangerous to expose on a public internet URL. Locally the default stays enabled for the classroom SQL demonstration. Do not deploy real money or sensitive user data.

## A. Prepare the repository

1. Add these deployment files and source changes to your existing `morrow` repo.
2. Check `git status` and inspect every changed/untracked file. Never commit passwords, wallet files, `.env`, or Oracle credentials.
3. Commit and push to GitHub. Keep the project `pom.xml` at the repository root for Render's Dockerfile build context (or adapt `dockerfilePath`/`dockerContext` if the Maven project lives in a subfolder).

Example:

```powershell
git add .
git status
git commit -m "Prepare Morrow for cloud deployment"
git push origin main
```

## B. Provision a remote Oracle database

1. Create an Oracle Cloud account and provision an **Always Free Autonomous AI Transaction Processing Database** if available in your region. Oracle documents that Always Free Autonomous databases are available subject to region/account availability.
2. Configure a public endpoint with TLS authentication for the app host. Prefer a database access-control list limited to the app host's outbound IPs where the hosting plan gives stable egress IPs. Do not reuse the laptop's `XE` URL.
3. From the Autonomous Database details page, open **Database connection / Connection strings** and select the TLS connection string (not mTLS). Follow Oracle's current instructions to enable TLS authentication if needed. Build the JDBC URL by prefixing the descriptor with `jdbc:oracle:thin:@` when Oracle provides a descriptor. Use a current Oracle JDBC driver (the Docker cloud profile uses `ojdbc11` 23.26.3.0.0, documented for JDK 11/17/19/21).
4. As the database administrator, create a least-privilege application schema user. Example SQL, execute using the Autonomous DB admin worksheet; choose your own strong password:

```sql
CREATE USER MORROW_APP IDENTIFIED BY "REPLACE_WITH_A_UNIQUE_STRONG_PASSWORD";
GRANT CREATE SESSION, CREATE TABLE, CREATE VIEW, CREATE SEQUENCE, CREATE TRIGGER TO MORROW_APP;
GRANT UNLIMITED TABLESPACE TO MORROW_APP;
```

If your cloud database's user/table-space policy rejects a grant, use the cloud DB's documented schema-user procedure and grant only the privileges needed by the schema.
5. Connect to the cloud database as `MORROW_APP` through SQL Developer/SQLcl/SQL Worksheet and run `database/01_schema.sql` once. This script creates `USERS`, `WALLETS`, `TRANSACTIONS`, `LEDGER`, sequences, triggers, indexes, and constraints. It is for a fresh cloud schema; don't run it on a populated schema.
6. Create the first test account through the deployed website after step D. To promote that account, connect as `MORROW_APP` and run (replace the email with the account you just registered):

```sql
UPDATE USERS SET ROLE='ADMIN' WHERE LOWER(EMAIL)=LOWER('your-admin-test@example.com') AND STATUS='ACTIVE';
COMMIT;
SELECT USER_ID, EMAIL, ROLE, STATUS FROM USERS ORDER BY USER_ID;
```

Confirm exactly the intended account has `ADMIN` role. Register other accounts via Morrow so BCrypt hashes and wallets are created correctly.

## C. Deploy to Render

1. Push the repo to GitHub.
2. In Render, choose **New + → Web Service**, connect GitHub, and select `morrow`. You can also use Render Blueprints to read `render.yaml`.
3. Choose Docker runtime. Ensure the Dockerfile is at the configured repository root.
4. Add these service environment variables in Render's Environment settings; never put values in Git:
   - `ORACLE_JDBC_URL`: full secure TLS JDBC URL/descriptor for the remote Autonomous Database.
   - `ORACLE_DB_USER`: `MORROW_APP` (or the exact app schema user you created).
   - `ORACLE_DB_PASSWORD`: that schema user's database password.
   - `SESSION_COOKIE_SECURE`: `true`.
   - `MORROW_ADMIN_SQL_ENABLED`: `false` (recommended for a public service).
5. The Docker build uses Maven profile `cloud`, switching to `ojdbc11` 23.26.3.0.0 for JDK 21 and the cloud Oracle DB. It runs on the Render-provided `PORT` and binds to `0.0.0.0`.
6. Deploy and inspect Render build/runtime logs. Test `https://YOUR-SERVICE.onrender.com/health` and then `/health/db`. `/health` only confirms the app process; `/health/db` must say `status: ok` before database-backed features will work.
7. If `/health/db` fails, inspect Render logs for Oracle error codes. Common causes include wrong JDBC TLS descriptor, incorrect app-schema credentials, database ACL/network settings, or missing schema tables.

## D. Create account and admin

1. Register the first hosted demo account using the normal user registration page.
2. Promote it to `ADMIN` using the SQL in section B. This preserves the account and wallet.
3. Log in at `/admin`. The SQL workbench is disabled on the hosted demo, but user/wallet/transaction/ledger management remains available. Use local SQL*Plus for raw DDL/DML demonstrations.
4. Test with disposable accounts and simulated funds only.

## E. Add the live URL to GitHub

After Render gives you a working URL, add this to the top of `README.md`, replacing the placeholder:

```markdown
## Live demo
- **Website:** https://YOUR-SERVICE.onrender.com
- **Admin login:** https://YOUR-SERVICE.onrender.com/admin
- **Database health:** https://YOUR-SERVICE.onrender.com/health/db

> Academic demo only. All wallet funds are simulated. Public deployment disables the raw SQL workbench.
```

Then commit and push the README update.

## Verify before sharing

- `/health/db` returns `status: ok`.
- Normal user can register/sign in and only access their own wallet.
- Admin can sign in at `/admin` and manage the database records through supported admin screens.
- Hosted SQL workbench is disabled (`MORROW_ADMIN_SQL_ENABLED=false`).
- A top-up/transfer updates cloud `WALLETS`, `TRANSACTIONS`, and `LEDGER`.
- No database passwords, wallet files, or secrets are in GitHub history.

Official references:
- Render Docker: https://render.com/docs/docker
- Render environment variables: https://render.com/docs/configure-environment-variables
- Oracle Autonomous Database TLS/JDBC: https://docs.oracle.com/en-us/iaas/autonomous-database-serverless/doc/connect-introduction.html
- Oracle Always Free Autonomous Database: https://docs.oracle.com/en-us/iaas/autonomous-database-serverless/doc/autonomous-always-free.html
