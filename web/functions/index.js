/**
 * RGS — scheduled weekly team report email.
 * Runs every Monday 09:00 IST, emails a summary to admins + managers.
 *
 * Needs (one-time): Blaze plan + a Gmail App Password. See SETUP.md.
 */
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const nodemailer = require("nodemailer");

admin.initializeApp();
const db = admin.firestore();

const GMAIL_EMAIL = defineSecret("GMAIL_EMAIL");       // sender Gmail address
const GMAIL_PASSWORD = defineSecret("GMAIL_PASSWORD");  // Gmail App Password (not the normal password)

const ADMINS = ["info4mediaearth@gmail.com"]; // keep in sync with firestore.rules isAdmin()

exports.weeklyReport = onSchedule(
  { schedule: "0 9 * * 1", timeZone: "Asia/Kolkata", secrets: [GMAIL_EMAIL, GMAIL_PASSWORD] },
  async () => {
    const weekAgo = Date.now() - 7 * 24 * 60 * 60 * 1000;

    // 1. Pull every surveyor's backup (same source as the dashboard).
    const snap = await db.collectionGroup("backup").get();
    const sv = {};
    snap.forEach((d) => {
      const uid = d.ref.parent.parent.id;
      let v = null;
      try { v = JSON.parse(d.get("json") || "null"); } catch (e) { /* skip */ }
      (sv[uid] = sv[uid] || {})[d.id] = v;
    });

    // 2. Build per-surveyor last-7-days summary.
    let totalSurveyed = 0, totalInterested = 0;
    const rows = [];
    for (const uid of Object.keys(sv)) {
      const s = sv[uid];
      const p = s.profile || {};
      const name = [p.name, p.surname].filter(Boolean).join(" ") || p.fullName || p.mobile || uid.slice(0, 6);
      const recces = (s.recces || []).filter((r) => (r.createdAt || 0) >= weekAgo);
      const interested = recces.filter((r) => r.status === "Interested").length;
      totalSurveyed += recces.length;
      totalInterested += interested;
      if (recces.length) rows.push({ name, surveyed: recces.length, interested });
    }
    rows.sort((a, b) => b.surveyed - a.surveyed);

    const html = `<h2 style="font-family:Arial">RGS — Weekly Team Report</h2>
      <p style="font-family:Arial">Last 7 days &middot; Surveyed: <b>${totalSurveyed}</b> &middot; Interested: <b>${totalInterested}</b></p>
      <table border="1" cellpadding="7" cellspacing="0" style="border-collapse:collapse;font-family:Arial;font-size:14px">
        <tr style="background:#FFC400"><th align="left">Surveyor</th><th>Surveyed</th><th>Interested</th></tr>
        ${rows.map((r) => `<tr><td>${r.name}</td><td align="center">${r.surveyed}</td><td align="center">${r.interested}</td></tr>`).join("") || `<tr><td colspan="3">No activity this week.</td></tr>`}
      </table>
      <p style="font-family:Arial;color:#888;font-size:12px">Full dashboard: https://recee-gps-stamp.web.app</p>`;

    // 3. Recipients = admins + managers (from config/access).
    let recipients = ADMINS.slice();
    try {
      const acc = await db.doc("config/access").get();
      const m = (acc.exists && acc.data().managers) || [];
      recipients = recipients.concat(m);
    } catch (e) { /* ignore */ }
    recipients = [...new Set(recipients.filter(Boolean))];

    // 4. Send.
    const t = nodemailer.createTransport({
      service: "gmail",
      auth: { user: GMAIL_EMAIL.value(), pass: GMAIL_PASSWORD.value() },
    });
    await t.sendMail({
      from: `RGS Dashboard <${GMAIL_EMAIL.value()}>`,
      to: recipients.join(","),
      subject: "RGS — Weekly Team Report",
      html,
    });
    console.log("Weekly report sent to", recipients);
  }
);
