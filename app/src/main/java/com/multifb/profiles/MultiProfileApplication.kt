package com.multifb.profiles

import android.app.Application
import android.content.Context
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.ClientConfiguration
import com.multifb.profiles.monitor.FacebookLifecycleCallback

/** Application utama sekaligus bootstrap BlackBox pada setiap proses host. */
class MultiProfileApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        BlackBoxCore.get().doAttachBaseContext(
            base,
            object : ClientConfiguration() {
                override fun getHostPackageName(): String = base.packageName
            }
        )
    }

    override fun onCreate() {
        super.onCreate()
        val core = BlackBoxCore.get()
        core.doCreate()

        // Callback ini dipasang di proses virtual. BlackBox memanggil
        // afterApplicationOnCreate untuk tiap aplikasi/user virtual.
        if (core.isBlackProcess()) {
            core.addAppLifecycleCallback(FacebookLifecycleCallback())
        }
    }
}
