package com.receegpsstamp

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class RgsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // PdfBox-Android must load its resources once before any PDF is generated.
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
        // Fleet maintenance reminders — notification channel + a periodic background check.
        com.receegpsstamp.feature.fleet.FleetNotifications.createChannel(this)
        com.receegpsstamp.feature.fleet.FleetNotifications.schedule(this)
        // Save any uncaught crash trace to a file so it can be shared for diagnosis.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = File(getExternalFilesDir(null), "crash").apply { mkdirs() }
                File(dir, "crash_${System.currentTimeMillis()}.txt")
                    .writeText(android.util.Log.getStackTraceString(throwable))
            } catch (_: Throwable) { /* never block the crash path */ }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
