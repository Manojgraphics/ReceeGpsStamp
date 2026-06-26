package com.receegpsstamp.feature.fleet

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.receegpsstamp.MainActivity
import com.receegpsstamp.data.local.LocalStore
import java.io.File
import java.util.concurrent.TimeUnit

object FleetNotifications {
    const val CHANNEL_ID = "fleet_alerts"
    private const val NOTIF_ID = 4201

    fun createChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Fleet alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Vehicle service, oil change, insurance & PUC reminders"
            }
            ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    /** Runs the maintenance check roughly twice a day in the background (survives app close). */
    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<FleetAlertWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("fleet_alerts", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun show(ctx: Context, alerts: List<String>) {
        if (alerts.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val pi = PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Fleet — ${alerts.size} reminder" + if (alerts.size > 1) "s" else "")
            .setContentText(alerts.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(alerts.joinToString("\n")))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n) } catch (_: SecurityException) { /* permission revoked */ }
    }
}

/** Reads the local data file directly (no Hilt needed) so the default WorkerFactory can run it. */
class FleetAlertWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        try {
            val file = File(applicationContext.filesDir, "rgs_data.json")
            if (file.exists()) {
                val db = Gson().fromJson(file.readText(), LocalStore.Db::class.java)
                if (db != null) FleetNotifications.show(applicationContext, FleetAlerts.alertsFor(db.vehicles))
            }
        } catch (_: Exception) { /* never crash the background job */ }
        return Result.success()
    }
}
