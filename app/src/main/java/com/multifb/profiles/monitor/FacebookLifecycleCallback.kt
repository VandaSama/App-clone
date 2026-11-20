package com.multifb.profiles.monitor

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.AppLifecycleCallback

/**
 * Dipanggil oleh BlackBox setelah Application Facebook virtual dibuat.
 * Callback Activity didaftarkan ke Application Facebook, bukan Activity host.
 */
class FacebookLifecycleCallback : AppLifecycleCallback() {
    private var virtualUserId: Int = -1
    private var virtualProcessName: String = "unknown"

    override fun afterApplicationOnCreate(
        packageName: String,
        processName: String,
        application: Application,
        userId: Int
    ) {
        if (packageName != FACEBOOK_PACKAGE) return

        virtualUserId = userId
        virtualProcessName = processName
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        sendActivityState(activity, isResumed = true)
    }

    override fun onActivityPaused(activity: Activity) {
        sendActivityState(activity, isResumed = false)
    }

    private fun sendActivityState(activityForEvent: Activity, isResumed: Boolean) {
        if (virtualUserId < 1) return
        val hostContext: Context = BlackBoxCore.getContext()
        val intent = Intent(ProfileMonitorService.ACTION_ACTIVITY_STATE)
            .setPackage(BlackBoxCore.getHostPkg())
            .putExtra(ProfileMonitorService.EXTRA_USER_ID, virtualUserId)
            .putExtra(ProfileMonitorService.EXTRA_PROCESS_NAME, virtualProcessName)
            .putExtra(ProfileMonitorService.EXTRA_ACTIVITY_ID, System.identityHashCode(activityForEvent))
            .putExtra(ProfileMonitorService.EXTRA_RESUMED, isResumed)
        hostContext.sendBroadcast(intent)
    }

    companion object {
        private const val FACEBOOK_PACKAGE = "com.facebook.katana"
    }
}
