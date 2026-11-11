package com.babunator.scanner

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class ScannerApp : Application() {

    private var asked = false
    private var crashChecked = false

    override fun onCreate() {
        super.onCreate()

        installCrashLogger()

        registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?
                ) {
                }

                override fun onActivityStarted(activity: Activity) {
                }

                override fun onActivityResumed(activity: Activity) {
                    askNotificationPermission(activity)
                    showLastCrash(activity)
                }

                override fun onActivityPaused(activity: Activity) {
                }

                override fun onActivityStopped(activity: Activity) {
                }

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle
                ) {
                }

                override fun onActivityDestroyed(activity: Activity) {
                }
            }
        )
    }

    // Saves the reason of a crash to a file, so it can be shown on the
    // next launch (and copied or shared by the user). The text is kept
    // short: very long texts cannot be copied or shared.
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val writer = StringWriter()
                error.printStackTrace(PrintWriter(writer))

                File(filesDir, "last_crash.txt").writeText(
                    (
                            java.time.LocalDateTime.now().toString() +
                                    "\n" +
                                    writer.toString()
                            ).take(MAX_CRASH_CHARS)
                )
            } catch (_: Throwable) {
            }

            previous?.uncaughtException(thread, error)
        }
    }

    private fun showLastCrash(activity: Activity) {
        if (crashChecked) return
        crashChecked = true

        try {
            val file = File(filesDir, "last_crash.txt")
            if (!file.exists()) return

            val text = file.readText().take(MAX_CRASH_CHARS)

            file.delete()

            AlertDialog.Builder(activity)
                .setTitle("App closed unexpectedly last time")
                .setMessage(text.take(4000))
                .setPositiveButton("COPY") { _, _ ->
                    try {
                        val manager =
                            activity.getSystemService(Context.CLIPBOARD_SERVICE)
                                    as ClipboardManager

                        manager.setPrimaryClip(
                            ClipData.newPlainText("crash", text)
                        )
                    } catch (_: Throwable) {
                    }
                }
                .setNeutralButton("SHARE") { _, _ ->
                    try {
                        val intent = Intent(Intent.ACTION_SEND)
                        intent.type = "text/plain"
                        intent.putExtra(Intent.EXTRA_TEXT, text)

                        val chooser =
                            Intent.createChooser(intent, "Send crash log")

                        activity.startActivity(chooser)
                    } catch (_: Throwable) {
                    }
                }
                .setNegativeButton("CLOSE", null)
                .show()

        } catch (_: Throwable) {
        }
    }

    // Android 13+ needs runtime permission before notifications can be shown.
    private fun askNotificationPermission(activity: Activity) {
        if (asked) return
        if (Build.VERSION.SDK_INT < 33) return

        asked = true

        val granted =
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        if (granted) return

        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            2001
        )
    }

    companion object {
        private const val MAX_CRASH_CHARS = 20000
    }
}
