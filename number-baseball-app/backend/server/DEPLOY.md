# Deploying the game server on Render

This guide puts the Number Baseball server online on [Render](https://render.com) with a persistent disk, so accounts and records
survive restarts. No programming is needed; you click through Render's website. Allow about 20 minutes.

## What you need

- A Render account (sign in with GitHub) and access to the GitHub repository that contains `number-baseball-app/`.
- A paid **Starter** (or larger) web service: the free plan goes to sleep after 15 minutes and has no persistent disk, which would make
  online play look broken to players and Play reviewers and would lose every account on restart.
- Optional but recommended: your own domain name (see "Custom domain").

## 1. Create the web service

1. In the Render dashboard click **New → Web Service** and pick the repository.
2. Fill in:
   - **Name**: something neutral, for example `numbaseball-server` (never your real name; it appears in the default URL).
   - **Region**: the one closest to most players (for Korea: Singapore).
   - **Branch**: `main` (or the release branch the tech lead tells you).
   - **Root Directory**: `number-baseball-app`
   - **Runtime / Language**: `Docker`
   - **Dockerfile Path**: `backend/server/Dockerfile`
   - **Docker Build Context Directory**: `.` (that is, `number-baseball-app`)
   - **Instance Type**: `Starter`.
3. **Health Check Path** (under Advanced): `/health`.

## 2. Add the persistent disk

Under **Advanced → Disks → Add Disk**:

- **Name**: `data`
- **Mount Path**: `/data`
- **Size**: 1 GB is plenty to start.

The server keeps its database (`/data/numbaseball.db`) there. With a disk attached Render runs exactly one instance, which is what the
server needs (games live in the memory of that one instance).

## 3. Environment variables

Under **Environment** add:

| Key | Value | Notes |
| --- | --- | --- |
| `ADMIN_TOKEN` | a long random password (at least 16 characters; use Render's **Generate** button) | needed for the moderation tools below. Keep it secret. |
| `APP_ADS_TXT` | the exact text AdMob gives you for `app-ads.txt` | several lines are fine. Leave it out until AdMob is set up. |
| `REVIEW_ROOM_CODE` | 5 characters from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`, for example `PLAYR` | a private room code that always starts a game against a bot, for Google Play reviewers. Write it into the Play Console "App access" instructions. |
| `QUICK_MATCH_FREE_CHAT` | `false` (default) | `true` would allow free-text chat in quick match. Keep `false`. |
| `MIN_AGE` | `14` (default) | the minimum age the app asks players to confirm. |

Do **not** set `PORT` or `DATA_DIR`: the image already uses `8080` and `/data`. Never set `RELAXED_RATE_LIMITS` or `TEST_HOOKS` on the
real server; they are only for test servers.

Click **Create Web Service**. The first build takes a few minutes. When the log shows `Responding at http://0.0.0.0:8080`, open
`https://<your-service>.onrender.com/health`; it should show `{"status":"ok"}`.

## 4. Check the public pages

These URLs go into the Play Console (privacy policy, account deletion, developer website):

- `https://<your-domain>/privacy`
- `https://<your-domain>/terms` (the child-safety section is `https://<your-domain>/terms#child-safety`)
- `https://<your-domain>/delete-account`
- `https://<your-domain>/app-ads.txt` (only after `APP_ADS_TXT` is set)

The texts come from `docs/play/*.md` in the repository. After the release lead edits them, Render rebuilds automatically on the next push
(or click **Manual Deploy → Deploy latest commit**).

## 5. Custom domain (recommended)

Settings → **Custom Domains → Add**, enter your domain (for example `numbaseball.example`), and copy the DNS records Render shows into your
domain registrar's DNS page. Render issues the HTTPS certificate by itself. The server redirects plain `http://` to `https://`. Use this
domain in the app build, the Play Console and AdMob.

## 6. Moderation and deletion requests by email

All moderation uses the admin API with your `ADMIN_TOKEN`. The simplest tool is `curl` in a terminal (macOS/Linux; on Windows use
PowerShell's `curl.exe`). Replace `TOKEN` and the domain.

Open reports (check at least once a day):

```bash
curl -H "Authorization: Bearer TOKEN" https://your-domain/admin/v1/reports
```

Mark a report as handled:

```bash
curl -X POST -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" \
     -d '{"resolution":"warned"}' https://your-domain/admin/v1/reports/12/resolve
```

Restrict someone's chat for 24 hours (0 lifts it), or ban them (use the `targetId` from the report):

```bash
curl -X POST -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" \
     -d '{"hours":24,"reason":"insults"}' https://your-domain/admin/v1/accounts/PLAYER_ID/chat-restrict
curl -X POST -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" \
     -d '{"reason":"repeated abuse"}' https://your-domain/admin/v1/accounts/PLAYER_ID/ban
```

A player who no longer has the app emails their name, for example `Mina#0420`. Write `#` as `%23` in the address. First look the account
up and compare `lastActiveAt` (milliseconds since 1970; paste it into any "epoch converter") with the date they say they last played:

```bash
curl -H "Authorization: Bearer TOKEN" "https://your-domain/admin/v1/accounts?handle=Mina%230420"
```

Then delete it (this cannot be undone):

```bash
curl -X DELETE -H "Authorization: Bearer TOKEN" "https://your-domain/admin/v1/accounts?handle=Mina%230420"
```

Three reports from different players within 24 hours restrict a player's chat automatically for 24 hours; review those reports too.

## 7. Updates, restarts and backups

- **Deploys restart the server.** Games in progress are lost (players see "the server restarted" and no result is recorded); accounts and
  records are safe on the disk. Deploy at quiet times.
- **Logs**: Render shows them under **Logs**. The server never writes chat text, tokens, codes or IP addresses into its log, but Render's
  own request logs may contain IP addresses; check Render's current log retention period and tell the release lead (it goes into the
  privacy policy).
- **Backups**: Render takes automatic snapshots of persistent disks; check the current schedule and retention under the disk's settings and
  tell the release lead. To download a copy yourself, use the service's **Shell** tab: `ls -l /data`.
- If the disk is ever full, increase its size under **Disks** (it cannot be shrunk).

## Running a test server (for QA)

Same steps with a different name, plus `RELAXED_RATE_LIMITS=true` (allows many test accounts from one address) and `TEST_HOOKS=true`
(lets tests move the server clock with `POST /admin/v1/test/clock?advanceMs=...`). Locally:
`docker build -f backend/server/Dockerfile -t nb .` from `number-baseball-app/`, then
`docker run -p 8080:8080 -v nbdata:/data -e ADMIN_TOKEN=test-admin-token-123 -e RELAXED_RATE_LIMITS=true -e TEST_HOOKS=true nb`.
