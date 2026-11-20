package com.multifb.profiles

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.multifb.profiles.data.FbProfile
import com.multifb.profiles.data.ProfileStore
import com.multifb.profiles.engine.BlackBoxProfileController
import com.multifb.profiles.engine.ProfileCacheCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var profileStore: ProfileStore
    private lateinit var profileController: BlackBoxProfileController
    private var resumeRevision by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileStore = ProfileStore(applicationContext)
        profileController = BlackBoxProfileController(applicationContext, profileStore)

        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF1877F2))) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MultiProfileScreen(profileStore, profileController, resumeRevision)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeRevision += 1
        // Bersihkan hanya cache dan code_cache semua profil saat layar utama kembali dibuka.
        if (::profileStore.isInitialized) {
            lifecycleScope.launch(Dispatchers.IO) {
                profileStore.profiles.first().forEach { profile ->
                    runCatching { ProfileCacheCleaner.clear(profile.userId) }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun MultiProfileScreen(
    profileStore: ProfileStore,
    controller: BlackBoxProfileController,
    resumeRevision: Int
) {
    val context = LocalContext.current
    val profiles by profileStore.profiles.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var addDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FbProfile?>(null) }
    var deleteTarget by remember { mutableStateOf<FbProfile?>(null) }
    var busy by remember { mutableStateOf(false) }

    val facebookInstalled = remember(resumeRevision) { controller.isFacebookInstalledOnHost() }
    val abiDescription = remember(resumeRevision) { controller.facebookAbiDescription() }

    fun showMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun runBusy(task: suspend () -> Unit) {
        scope.launch {
            busy = true
            try {
                task()
            } catch (error: Throwable) {
                showMessage(error.message ?: "Terjadi kesalahan.")
            } finally {
                busy = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Multi Profil FB", fontWeight = FontWeight.Bold)
                        Text("Facebook resmi dalam profil terpisah", fontSize = 12.sp)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (facebookInstalled) "Facebook resmi terdeteksi" else "Facebook belum terpasang",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (facebookInstalled) abiDescription
                        else "Pasang aplikasi Facebook resmi (com.facebook.katana) di HP sebelum membuat profil."
                    )
                    Text(
                        "Profil tersimpan: ${profiles.size}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Button(
                onClick = { addDialog = true },
                enabled = facebookInstalled && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tambah Profil")
            }

            if (busy) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
            }

            if (profiles.isEmpty()) {
                Text(
                    "Belum ada profil. Tekan Tambah Profil untuk membuat ruang Facebook baru.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(profiles, key = { it.userId }) { profile ->
                        ProfileCard(
                            profile = profile,
                            enabled = !busy,
                            onOpen = {
                                runCatching {
                                    controller.openProfile(profile.userId)
                                }.onFailure { showMessage(it.message ?: "Gagal membuka Facebook.") }
                            },
                            onRename = { renameTarget = profile },
                            onShortcut = {
                                runCatching {
                                    val pinned = controller.createPinnedShortcut(profile)
                                    showMessage(
                                        if (pinned) "Permintaan pintasan dikirim ke launcher."
                                        else "Launcher tidak mendukung pintasan atau permintaan ditolak."
                                    )
                                }.onFailure { showMessage(it.message ?: "Gagal membuat pintasan.") }
                            },
                            onDelete = { deleteTarget = profile }
                        )
                    }
                }
            }

            Text(
                "Catatan: setiap profil memakai userId BlackBox sendiri. Data login tidak ikut dibersihkan saat cache dihapus.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (addDialog) {
        NameDialog(
            title = "Tambah Profil",
            initialName = "Profil ${profiles.size + 1}",
            confirmText = "Buat Profil",
            onDismiss = { addDialog = false },
            onConfirm = { name ->
                addDialog = false
                runBusy {
                    val profile = controller.createProfile(name)
                    showMessage("${profile.name} berhasil dibuat (userId ${profile.userId}).")
                }
            }
        )
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "Ubah Nama Profil",
            initialName = target.name,
            confirmText = "Simpan",
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                renameTarget = null
                runBusy {
                    controller.renameProfile(target, newName)
                    showMessage("Nama profil berhasil diperbarui.")
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Hapus ${target.name}?") },
            text = {
                Text("Seluruh data virtual user ini, termasuk sesi login Facebook, akan dihapus. Data Facebook di HP tidak ikut terhapus.")
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    runBusy {
                        controller.deleteProfile(target)
                        showMessage("${target.name} dan datanya telah dihapus.")
                    }
                }) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Batal") }
            }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: FbProfile,
    enabled: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onShortcut: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Virtual user ID: ${profile.userId}", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onOpen, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text("Buka")
                }
                OutlinedButton(onClick = onRename, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text("Ubah Nama")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = onShortcut, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text("Buat Pintasan")
                }
                TextButton(onClick = onDelete, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text("Hapus", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    initialName: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(title, initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Nama profil") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}
