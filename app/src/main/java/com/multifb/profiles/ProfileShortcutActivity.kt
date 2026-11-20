package com.multifb.profiles

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.multifb.profiles.data.ProfileStore
import com.multifb.profiles.engine.BlackBoxProfileController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Activity perantara yang dipanggil ikon profil di home screen. */
class ProfileShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val userId = intent.getIntExtra(EXTRA_USER_ID, -1)
        if (userId < 1) {
            finish()
            return
        }

        lifecycleScope.launch {
            try {
                val store = ProfileStore(applicationContext)
                val profile = store.profiles.first().firstOrNull { it.userId == userId }
                check(profile != null) { "Profil ini sudah dihapus dari Multi Profil FB." }
                val controller = BlackBoxProfileController(applicationContext, store)
                controller.openProfile(userId)
            } catch (error: Throwable) {
                Toast.makeText(
                    this@ProfileShortcutActivity,
                    error.message ?: "Tidak dapat membuka profil Facebook.",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                finish()
            }
        }
    }

    companion object {
        const val EXTRA_USER_ID = "extra_shortcut_user_id"
    }
}
