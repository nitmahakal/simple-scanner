package com.babunator.scanner

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class ScannerApp : Application() {

    private var asked = false

    override fun onCreate() {
        super.onCreate()

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
}
