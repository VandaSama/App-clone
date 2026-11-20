package com.multifb.profiles.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.multifb.profiles.R
import com.multifb.profiles.engine.ProfileCacheCleaner
import top.niunaijun.blackbox.BlackBoxCore

/**
 * Foreground service agar timer 10 detik tetap berjalan saat UI host tidak
 * terlihat. State dilacak per virtual user dan per proses Facebook.
 */
class ProfileMonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    // userId -> processName -> identityHashCode Activity yang sedang resumed.
    private val processForeground = mutableMapOf<Int, MutableMap<String, MutableSet<Int>>>()
    private val stopTasks = mutableMapOf<Int, Runnable>()
    private var receiverRegistered = false
    private val idleStopTask = Runnable {
        if (!hasForegroundActivity() && stopTasks.isEmpty()) {
            processForeground.clear()
            stopSelf()
        }
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val userId = intent.getIntExtra(EXTRA_USER_ID, -1)
            if (userId < 1) return

            when (intent.action) {
                ACTION_ACTIVITY_STATE -> {
                    val processName = intent.getStringExtra(EXTRA_PROCESS_NAME) ?: "unknown"
                    val activityId = intent.getIntExtra(EXTRA_ACTIVITY_ID, -1)
                    val resumed = intent.getBooleanExtra(EXTRA_RESUMED, false)
                    updateActivityState(userId, processName, activityId, resumed)
                }
                ACTION_CANCEL_USER -> cancelUser(userId)
                ACTION_FORGET_USER -> forgetUser(userId)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        val filter = IntentFilter().apply {
            addAction(ACTION_ACTIVITY_STATE)
            addAction(ACTION_CANCEL_USER)
            addAction(ACTION_FORGET_USER)
        }
        ContextCompat.registerReceiver(
            this,
            stateReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        receiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Kalau peluncuran virtual gagal sebelum callback lifecycle pertama,
        // jangan biarkan foreground service hidup tanpa batas.
        handler.removeCallbacks(idleStopTask)
        handler.postDelayed(idleStopTask, IDLE_TIMEOUT_MS)
        return START_STICKY
    }

    private fun updateActivityState(
        userId: Int,
        processName: String,
        activityId: Int,
        resumed: Boolean
    ) {
        handler.removeCallbacks(idleStopTask)
        val perProcess = processForeground.getOrPut(userId) { mutableMapOf() }
        val resumedActivities = perProcess.getOrPut(processName) { mutableSetOf() }

        if (resumed) {
            if (activityId >= 0) resumedActivities.add(activityId)
            cancelStopTask(userId)
            return
        }

        if (activityId >= 0) resumedActivities.remove(activityId)
        // Jangan hentikan profil jika Activity lain / proses Facebook lain masih aktif.
        val anyProcessResumed = perProcess.values.any { it.isNotEmpty() }
        if (!anyProcessResumed) scheduleStop(userId)
    }

    private fun scheduleStop(userId: Int) {
        cancelStopTask(userId)
        val task = Runnable {
            stopTasks.remove(userId)
            val stillForeground = processForeground[userId]?.values?.any { it.isNotEmpty() } == true
            if (!stillForeground) {
                // API BlackBox ini hanya menghentikan package pada userId ini.
                BlackBoxCore.get().stopPackage(FACEBOOK_PACKAGE, userId)
                ProfileCacheCleaner.clear(userId)
                processForeground.remove(userId)
            }
            stopSelfIfIdle()
        }
        stopTasks[userId] = task
        handler.postDelayed(task, BACKGROUND_GRACE_MS)
    }

    private fun cancelStopTask(userId: Int) {
        stopTasks.remove(userId)?.let(handler::removeCallbacks)
    }

    /** Dipakai saat profil dibuka: batalkan timer lama, jangan matikan service. */
    private fun cancelUser(userId: Int) {
        cancelStopTask(userId)
        handler.removeCallbacks(idleStopTask)
        handler.postDelayed(idleStopTask, IDLE_TIMEOUT_MS)
    }

    /** Dipakai saat profil dihapus dari daftar. */
    private fun forgetUser(userId: Int) {
        cancelStopTask(userId)
        processForeground.remove(userId)
        stopSelfIfIdle()
    }

    private fun hasForegroundActivity(): Boolean = processForeground.values.any { processes ->
        processes.values.any { activities -> activities.isNotEmpty() }
    }

    private fun stopSelfIfIdle() {
        if (stopTasks.isEmpty() && !hasForegroundActivity()) {
            processForeground.clear()
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pemantau profil Facebook",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Menjaga timer penutupan profil virtual tetap berjalan"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_fb)
            .setContentTitle("Multi Profil FB aktif")
            .setContentText("Memantau aktivitas profil Facebook virtual")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(idleStopTask)
        stopTasks.values.forEach(handler::removeCallbacks)
        stopTasks.clear()
        if (receiverRegistered) unregisterReceiver(stateReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_ACTIVITY_STATE = "com.multifb.profiles.ACTION_ACTIVITY_STATE"
        const val ACTION_CANCEL_USER = "com.multifb.profiles.ACTION_CANCEL_USER"
        const val ACTION_FORGET_USER = "com.multifb.profiles.ACTION_FORGET_USER"
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_PROCESS_NAME = "extra_process_name"
        const val EXTRA_ACTIVITY_ID = "extra_activity_id"
        const val EXTRA_RESUMED = "extra_resumed"

        private const val FACEBOOK_PACKAGE = "com.facebook.katana"
        private const val CHANNEL_ID = "multi_profile_fb_monitor"
        private const val NOTIFICATION_ID = 2901
        private const val BACKGROUND_GRACE_MS = 10_000L
        private const val IDLE_TIMEOUT_MS = 30_000L

        fun start(context: Context) {
            val intent = Intent(context, ProfileMonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancelPendingForUser(context: Context, userId: Int, forget: Boolean = false) {
            val action = if (forget) ACTION_FORGET_USER else ACTION_CANCEL_USER
            val intent = Intent(action)
                .setPackage(context.packageName)
                .putExtra(EXTRA_USER_ID, userId)
            context.sendBroadcast(intent)
        }
    }
}
