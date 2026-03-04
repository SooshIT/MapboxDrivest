package com.drivest.navigation

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager

class DrivestApplication : Application(), Application.ActivityLifecycleCallbacks {

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        activity.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onActivityPaused(activity: Activity) {
        activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        // No-op
    }

    override fun onActivityStarted(activity: Activity) {
        // No-op
    }

    override fun onActivityStopped(activity: Activity) {
        // No-op
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // No-op
    }

    override fun onActivityDestroyed(activity: Activity) {
        // No-op
    }
}
