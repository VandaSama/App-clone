package com.multifb.profiles.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.content.pm.PackageManager
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.multifb.profiles.ProfileShortcutActivity
import com.multifb.profiles.data.FbProfile
import com.multifb.profiles.data.ProfileStore
import com.multifb.profiles.monitor.ProfileMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import top.niunaijun.blackbox.BlackBoxCore

/** Logika pembuatan dan pengelolaan virtual user Facebook. */
class BlackBoxProfileController(
    context: Context,
    private val profileStore: ProfileStore
) {
    private val appContext = context.applicationContext
    private val core = BlackBoxCore.get()

    fun isFacebookInstalledOnHost(): Boolean {
        return try {
            appContext.packageManager.getPackageInfo(FACEBOOK_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun facebookAbiDescription(): String {
        return try {
            val info = appContext.packageManager.getApplicationInfo(FACEBOOK_PACKAGE, 0)
            val dir = info.nativeLibraryDir.orEmpty().lowercase()
            when {
                dir.contains("arm64") -> "Facebook native ABI: arm64-v8a"
                dir.contains("/arm") || dir.endsWith("arm") -> "Facebook native ABI: armeabi-v7a"
                dir.contains("x86_64") -> "Facebook native ABI: x86_64 (tidak tersedia di APK ini)"
                dir.contains("x86") -> "Facebook native ABI: x86 (tidak tersedia di APK ini)"
                else -> "ABI Facebook tidak dapat dideteksi dari nativeLibraryDir"
            }
        } catch (_: PackageManager.NameNotFoundException) {
            "Facebook resmi belum terpasang"
        }
    }

    suspend fun createProfile(name: String): FbProfile = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "Nama profil tidak boleh kosong." }
        check(isFacebookInstalledOnHost()) {
            "Facebook resmi (com.facebook.katana) belum terpasang di HP."
        }

        val savedIds = profileStore.profiles.first().map { it.userId }.toSet()
        val engineIds = core.getUsers().orEmpty().map { it.id }.toSet()
        val usedIds = savedIds + engineIds
        var userId = maxOf(10, (usedIds.maxOrNull() ?: 9) + 1)
        while (usedIds.contains(userId)) userId++

        var userCreated = false
        try {
            val userInfo = core.createUser(userId)
                ?: error("BlackBox gagal membuat virtual user $userId.")
            userCreated = true

            // API ini memakai sourceDir paket Facebook yang sudah terpasang di host.
            val result = core.installPackageAsUser(FACEBOOK_PACKAGE, userId)
            if (!result.success || !core.isInstalled(FACEBOOK_PACKAGE, userId)) {
                error(result.msg?.takeIf { it.isNotBlank() }
                    ?: "BlackBox gagal memasang Facebook pada profil baru.")
            }

            val profile = FbProfile(userId, name.trim())
            profileStore.add(profile)
            profile
        } catch (error: Throwable) {
            if (userCreated) {
                runCatching { core.deleteUser(userId) }
            }
            throw error
        }
    }

    suspend fun renameProfile(profile: FbProfile, newName: String): FbProfile {
        val cleaned = newName.trim()
        require(cleaned.isNotEmpty()) { "Nama profil tidak boleh kosong." }
        profileStore.rename(profile.userId, cleaned)
        val updated = profile.copy(name = cleaned)
        updateShortcutLabel(updated)
        return updated
    }

    suspend fun deleteProfile(profile: FbProfile) = withContext(Dispatchers.IO) {
        ProfileMonitorService.cancelPendingForUser(appContext, profile.userId, forget = true)
        runCatching { core.stopPackage(FACEBOOK_PACKAGE, profile.userId) }
        // Menghapus user BlackBox menghapus data virtual user tersebut, bukan data host FB.
        core.deleteUser(profile.userId)
        profileStore.remove(profile.userId)
        disableShortcut(profile.userId)
    }

    fun openProfile(userId: Int) {
        check(isFacebookInstalledOnHost()) {
            "Facebook resmi belum terpasang. Pasang Facebook dari sumber resmi terlebih dahulu."
        }
        check(core.isInstalled(FACEBOOK_PACKAGE, userId)) {
            "Facebook belum terpasang pada profil virtual ini. Hapus lalu buat ulang profil."
        }

        // Pastikan timer lama dibatalkan sebelum cold start Facebook berikutnya.
        ProfileMonitorService.start(appContext)
        ProfileMonitorService.cancelPendingForUser(appContext, userId)
        check(core.launchApk(FACEBOOK_PACKAGE, userId)) {
            "BlackBox tidak menemukan Activity peluncuran Facebook untuk profil ini."
        }
    }

    fun createPinnedShortcut(profile: FbProfile): Boolean {
        check(isFacebookInstalledOnHost()) { "Facebook resmi belum terpasang." }
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(appContext)) return false
        return ShortcutManagerCompat.requestPinShortcut(
            appContext,
            buildShortcut(profile),
            null
        )
    }

    private fun updateShortcutLabel(profile: FbProfile) {
        runCatching {
            ShortcutManagerCompat.updateShortcuts(
                appContext,
                listOf(buildShortcut(profile))
            )
        }
    }

    private fun disableShortcut(userId: Int) {
        runCatching {
            ShortcutManagerCompat.disableShortcuts(
                appContext,
                listOf(shortcutId(userId)),
                "Profil Facebook ini sudah dihapus"
            )
        }
    }

    private fun buildShortcut(profile: FbProfile): ShortcutInfoCompat {
        val icon = facebookIcon()
        val intent = Intent(appContext, ProfileShortcutActivity::class.java).apply {
            action = ACTION_OPEN_PROFILE
            putExtra(ProfileShortcutActivity.EXTRA_USER_ID, profile.userId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return ShortcutInfoCompat.Builder(appContext, shortcutId(profile.userId))
            .setShortLabel(profile.name.take(25))
            .setLongLabel(profile.name)
            .setIcon(icon)
            .setIntent(intent)
            .setActivity(ComponentName(appContext, ProfileShortcutActivity::class.java))
            .build()
    }

    private fun facebookIcon(): IconCompat {
        val drawable: Drawable = try {
            appContext.packageManager.getApplicationIcon(FACEBOOK_PACKAGE)
        } catch (_: PackageManager.NameNotFoundException) {
            appContext.getDrawable(com.multifb.profiles.R.mipmap.ic_launcher)!!
        }
        val size = (96 * appContext.resources.displayMetrics.density).toInt().coerceAtLeast(96)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return IconCompat.createWithBitmap(bitmap)
    }

    companion object {
        const val FACEBOOK_PACKAGE = "com.facebook.katana"
        private const val ACTION_OPEN_PROFILE = "com.multifb.profiles.OPEN_PROFILE_SHORTCUT"
        private fun shortcutId(userId: Int) = "facebook_profile_$userId"
    }
}
