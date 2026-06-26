# Scheduled Weekly Email Report — one-time setup

The function `weeklyReport` (in `index.js`) emails a team summary to all
admins + managers every **Monday 09:00 IST**. It is written and ready — it just
needs these one-time steps because Cloud Functions require a billing account.

> 💡 Cost: the Blaze plan is pay-as-you-go but has a **generous free tier**.
> One weekly function run + a few emails stays at **₹0** in practice. You only
> pay if you go far beyond the free limits.

## Steps (run in **Command Prompt**, from the `web` folder)

1. **Enable Blaze** (one click, needs a card on file, but free tier applies):
   https://console.firebase.google.com/project/recee-gps-stamp/usage/details
   → "Modify plan" → Blaze.

2. **Make a Gmail App Password** for the sender Gmail (e.g. info4mediaearth):
   - Google Account → Security → 2-Step Verification (must be ON)
   - → App passwords → create one → copy the 16-character password.

3. **Set the secrets** (it will prompt you to paste each value):
   ```
   cd /d "D:\VS Code\ReceeGpsStamp\web"
   firebase functions:secrets:set GMAIL_EMAIL
   firebase functions:secrets:set GMAIL_PASSWORD
   ```

4. **Deploy:**
   ```
   firebase deploy --only functions
   ```

That's it — every Monday the report goes out automatically.

## Change the schedule
Edit the `schedule` line in `index.js` (cron format, IST):
`"0 9 * * 1"` = Mon 09:00. e.g. daily 08:00 = `"0 8 * * *"`. Then re-deploy.

## Test it now (without waiting for Monday)
After deploy, in the Firebase Console → Functions → `weeklyReport` → run, or:
`gcloud scheduler jobs run firebase-schedule-weeklyReport-<region>`
