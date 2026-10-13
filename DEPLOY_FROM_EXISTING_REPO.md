# Safe deployment path: keep Oracle PAT on `main`, deploy Neon on `neon-deploy`

This project is the PostgreSQL/Neon cloud edition. It is separate from your working Oracle XE 11g PAT version. Keep the Oracle version on `main` and deploy this edition from a new `neon-deploy` branch.

## 1. Back up and prepare the repo

1. Copy your current Oracle project folder to a backup location.
2. In the existing local clone of your GitHub `morrow` repository, open PowerShell at its root (the folder containing `.git`).
3. Check `git status`. Commit and push any current changes to `main` before continuing. Do not overwrite uncommitted work.

```powershell
git status
git switch main
git pull origin main
git switch -c neon-deploy
```

If `neon-deploy` already exists, use `git switch neon-deploy` instead of creating it again.

## 2. Copy this cloud edition into that branch

1. Extract `Morrow-Neon-Cloud-Project.zip` to a folder OUTSIDE the Git repository, for example `C:\Users\YOUR_NAME\Downloads\Morrow-Neon-Cloud`.
2. In File Explorer, open that extracted folder. Confirm it contains `pom.xml`, `Dockerfile`, `render.yaml`, `src`, and `database`.
3. Open the existing Git repository folder on branch `neon-deploy`.
4. Remove the old working-tree contents **except the hidden `.git` folder**. Then copy all contents from the extracted cloud project folder into the repository root. This replaces the Oracle code and database scripts with the Neon edition while retaining Git history.
5. Confirm the repository root has `pom.xml`, `Dockerfile`, `render.yaml`, `src`, and `database/01_schema_postgresql.sql`. The old Oracle SQL scripts should not remain in the cloud branch.

Do not copy the `.git` folder from anywhere and do not delete the repository's existing `.git` folder.

## 3. Commit and push the deployment branch

```powershell
git status --short
git add -A
git status --short
git commit -m "Add Neon PostgreSQL cloud deployment edition"
git push -u origin neon-deploy
```

Inspect both `git status` outputs. Never commit `.env` files or real credentials. The `.env.example` file contains placeholders only.

## 4. Create Neon database and deploy Render

Follow `README.md` from the new `neon-deploy` branch:

1. Create a Neon free PostgreSQL project.
2. Paste and run `database/01_schema_postgresql.sql` in the Neon SQL Editor once.
3. On Render, create a Blueprint from repository `morrow`, branch `neon-deploy`, and review `render.yaml`.
4. Set `NEON_JDBC_URL`, `NEON_DB_USER`, and `NEON_DB_PASSWORD` from Neon in Render's environment settings. Never commit those values.
5. Wait for the build to finish and test `/health` and `/health/db`.
6. Register the first account on the live website, promote it to `ADMIN` using `database/02_bootstrap_admin_postgresql.sql` (edit the email first), then sign in through `/admin`.

Render should use its Free web service plan in `render.yaml`. Do not select paid resources. On the free service, the app may spin down when idle; the first visit can take about a minute.
