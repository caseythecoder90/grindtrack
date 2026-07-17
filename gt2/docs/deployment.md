# Deployment (Hetzner VPS, behind existing nginx)

Prereqs already on the VPS: Docker + compose plugin, nginx, certbot with auto-renew.

## 1. Get the code up

Private GitHub repo (recommended — you will extend this):
```bash
# on the VPS — note: compose + app live in the gt2/ subfolder of the repo
git clone git@github.com:caseythecoder90/grindtrack.git && cd grindtrack/gt2
```

## 2. Secrets

```bash
cp .env.example .env
openssl rand -base64 48   # → JWT_SECRET
vim .env                  # set POSTGRES_PASSWORD, JWT_SECRET, GRINDTRACK_USERNAME/PASSWORD
chmod 600 .env
```
`.env` never leaves the VPS and is gitignored.

## 3. Build and start

```bash
docker compose up -d --build     # first build compiles UI + jar; takes a few minutes
docker compose logs -f app       # wait for "Started GrindtrackApplication"
docker compose logs app | grep -A4 "Bootstrap user"   # TOTP secret — add to authenticator NOW
```
The app listens on 127.0.0.1:8080 only — verify: `ss -tlnp | grep 8080` shows 127.0.0.1.

## 4. DNS + nginx + TLS

- DNS: A record `track.YOURDOMAIN.com` → VPS IP.
- Copy `nginx/track.conf.example` to `/etc/nginx/sites-available/track.conf`, fix
  `server_name`, symlink into `sites-enabled`, `nginx -t && systemctl reload nginx`.
- `certbot --nginx -d track.YOURDOMAIN.com` (adds TLS + redirect; renewal is already automated).
- Optional M1 preview: `openssl s_client -connect track.YOURDOMAIN.com:443 | openssl x509 -text`
  — read your own certificate chain.

## 5. Backups (do this the same day)

```bash
mkdir -p ~/backups
crontab -e
# nightly dump at 03:10, keep 14 days:
10 3 * * * cd ~/grindtrack/gt2 && docker compose exec -T db pg_dump -U grind grindtrack | gzip > ~/backups/grindtrack-$(date +\%F).sql.gz && ls -t ~/backups/grindtrack-*.sql.gz | tail -n +15 | xargs -r rm
```
Copy them **off** the VPS (from your laptop): `rsync -av vps:~/backups/ ~/vps-backups/` — cron
that too, or use a Hetzner Storage Box. Then restore one once to prove the loop:
```bash
gunzip -c grindtrack-YYYY-MM-DD.sql.gz | docker compose exec -T db psql -U grind -d grindtrack
```

## 6. Updating — automated via GitHub Actions

`.github/workflows/ci-cd.yml` (repo root) handles this:

- **Every PR to `main`**: backend `mvn verify` (compile + Spotless format check) and frontend
  `npm run build` (strict tsc + Vite) must pass before merge.
- **Every push to `main`** (including PR merges): after both builds pass, the deploy job SSHes
  into the VPS, hard-resets the checkout to `origin/main`, runs
  `docker compose up -d --build`, and fails the run if `/api/public/stats` isn't healthy
  within 150 s.

One-time setup — add three **repository secrets** (GitHub → Settings → Secrets and variables →
Actions):

| Secret | Value |
|---|---|
| `VPS_HOST` | VPS IP or hostname |
| `VPS_USER` | SSH user the app dir belongs to |
| `VPS_SSH_KEY` | Private key for that user (generate a dedicated pair: `ssh-keygen -t ed25519 -f deploy_key`; put `.pub` in the VPS `~/.ssh/authorized_keys`, paste the private half into the secret) |

Manual fallback is still just:
```bash
cd ~/grindtrack/gt2 && git pull && docker compose up -d --build
```
Liquibase applies any new changesets automatically on startup. Data lives in the `pgdata`
volume; `docker compose down` is safe, `down -v` destroys it.

## Checklist

- [ ] `ss -tlnp` shows 8080 and 5432 on loopback/internal only
- [ ] ufw allows only 22/80/443
- [ ] `.env` is chmod 600 and gitignored
- [ ] TOTP enrolled, bootstrap log lines cleared (force-recreate app)
- [ ] Backup cron installed AND one restore tested
- [ ] Login works from your phone over HTTPS
