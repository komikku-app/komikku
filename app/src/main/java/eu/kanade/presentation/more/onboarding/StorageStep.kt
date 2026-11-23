package eu.kanade.presentation.more.onboarding

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import com.hippo.unifile.UniFile
import eu.kanade.presentation.more.settings.screen.SettingsDataScreen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.flow.collectLatest
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.storage.service.StorageManager.Companion.directoryAccessible
import tachiyomi.domain.storage.service.StoragePreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

internal class StorageStep : OnboardingStep {

    private val storagePref = Injekt.get<StoragePreferences>().baseStorageDirectory()

    private var _isComplete by mutableStateOf(false)

    override val isComplete: Boolean
        get() = _isComplete

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val handler = LocalUriHandler.current

        // KMK -->
        val storageDir by storagePref.collectAsState()
        var locationValid by remember(storageDir) {
            mutableStateOf(directoryAccessible(context, storageDir))
        }

        var folderName by rememberSaveable { mutableStateOf(DEFAULT_FOLDER_NAME) }
        var isEditing by rememberSaveable { mutableStateOf(false) }
        var isLocked by rememberSaveable { mutableStateOf(false) }
        var showGuideDialog by rememberSaveable { mutableStateOf(false) }

        // Nama folder dibersihkan di sini agar folder yang dibuat dan hint SAF selalu sama
        val cleanName = folderName.toSafeFolderName()

        // Menyimpan folder pilihan pengguna, dipakai jalur otomatis maupun jalur manual
        fun savePickedFolder(uri: Uri): Boolean {
            val picked = UniFile.fromUri(context, uri) ?: return false
            // Set kosong dulu supaya perubahan terdeteksi walau folder yang sama dipilih ulang
            storagePref.set("")
            storagePref.set(picked.uri.toString())
            return true
        }

        // Tombol bawaan memakai pemilih yang sama dengan layar Setelan, dibuka di root
        val manualLauncher = SettingsDataScreen.storageLocationPicker(storagePref)

        val hintLauncher = rememberLauncherForActivityResult(
            contract = OpenDocumentTreeWithHint(),
        ) { uri ->
            if (uri == null) {
                // Pengguna menutup pemilih tanpa memilih, buka lagi akses edit nama folder
                isLocked = false
                return@rememberLauncherForActivityResult
            }

            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION

            // Sebagian perangkat tidak mendukung izin URI permanen, akses lewat URI tetap bisa dipakai
            try {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            } catch (e: SecurityException) {
                context.logcat(LogPriority.ERROR, e) { "Failed to acquire persistent folder access" }
                context.toast(MR.strings.file_picker_uri_permission_unsupported)
            }

            if (!savePickedFolder(uri)) {
                isLocked = false
                context.toast(MR.strings.file_picker_error)
            }
        }

        if (showGuideDialog) {
            AlertDialog(
                onDismissRequest = { },
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                title = {
                    Text(
                        text = stringResource(KMR.strings.onboarding_storage_permission_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(
                                KMR.strings.onboarding_storage_permission_dialog_desc,
                                cleanName,
                                stringResource(MR.strings.app_name),
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp,
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = {
                                showGuideDialog = false
                                val hintUri = documentsFolderUri(cleanName)
                                try {
                                    hintLauncher.launch(hintUri)
                                } catch (e: Exception) {
                                    context.logcat(LogPriority.ERROR, e) { "Failed to launch folder picker" }
                                    context.toast(MR.strings.file_picker_error)
                                    isLocked = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text(
                                text = stringResource(MR.strings.onboarding_storage_action_select),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                },
                confirmButton = {},
                shape = RoundedCornerShape(28.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            Surface(
                modifier = Modifier.size(100.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.CreateNewFolder,
                        contentDescription = null,
                        modifier = Modifier.size(50.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Text(
                text = stringResource(
                    KMR.strings.onboarding_storage_folder_info,
                    stringResource(MR.strings.app_name),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            // Bagian bawaan Komikku: pemilih sistem dibuka di root penyimpanan
            Text(
                text = stringResource(KMR.strings.onboarding_storage_pick_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(
                onClick = {
                    try {
                        manualLauncher.launch(null)
                    } catch (e: Exception) {
                        context.logcat(LogPriority.ERROR, e) { "Failed to launch folder picker" }
                        context.toast(MR.strings.file_picker_error)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLocked,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = stringResource(MR.strings.onboarding_storage_action_select),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // Bagian alternatif: Komikku yang membuatkan folder di Documents
            Text(
                text = stringResource(
                    KMR.strings.onboarding_storage_create_info,
                    stringResource(MR.strings.app_name),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            Surface(
                onClick = { if (!isLocked) isEditing = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isLocked) {
                        MaterialTheme.colorScheme.outlineVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
                // Kartu folder adalah satu-satunya elemen yang diangkat, tombol aksi di bawahnya datar
                tonalElevation = if (isLocked) 0.dp else 2.dp,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!isEditing) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(
                                    if (isLocked) {
                                        KMR.strings.onboarding_storage_folder_name_label_created
                                    } else {
                                        KMR.strings.onboarding_storage_folder_name_label
                                    },
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isLocked) {
                                    MaterialTheme.colorScheme.outline
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                            ) {
                                // Nama panjang tidak boleh mendorong ikon edit keluar kartu
                                Text(
                                    text = cleanName,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isLocked) {
                                        MaterialTheme.colorScheme.outline
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (!isLocked) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    // Ikon dekoratif: aksi edit sudah dibacakan oleh kartu
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = folderName,
                            onValueChange = { folderName = it },
                            label = { Text(stringResource(KMR.strings.onboarding_storage_folder_name_hint)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            ),
                            shape = RoundedCornerShape(20.dp),
                            trailingIcon = {
                                IconButton(onClick = { if (cleanName.isNotEmpty()) isEditing = false }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = stringResource(MR.strings.action_ok),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                        )
                    }
                }
            }

            // Jalur pintas: folder dibuat otomatis di Documents lalu pemilih diarahkan ke sana
            Button(
                onClick = {
                    if (cleanName.isNotEmpty()) {
                        isLocked = true
                        createFolderInDocuments(context, cleanName)
                        showGuideDialog = true
                    } else {
                        isEditing = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !isLocked,
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = stringResource(KMR.strings.onboarding_storage_folder_create),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Text(
                text = stringResource(MR.strings.onboarding_storage_help_info, stringResource(MR.strings.app_name)),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { handler.openUri(SettingsDataScreen.HELP_URL) },
            ) {
                Text(stringResource(MR.strings.onboarding_storage_help_action))
            }
        }
        // KMK <--

        LaunchedEffect(/* KMK --> */storageDir/* KMK <-- */) {
            storagePref.changes()
                .collectLatest {
                    // KMK -->
                    locationValid = directoryAccessible(context, storageDir)
                    _isComplete = locationValid
                    // KMK <--
                }
        }
    }

    // KMK -->
    private companion object {
        private const val DEFAULT_FOLDER_NAME = "Komikku"
    }
    // KMK <--
}

// KMK -->
private fun String.toSafeFolderName(): String = trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")

private fun documentsFolderUri(folderName: String): Uri =
    "content://com.android.externalstorage.documents/document/primary:Documents%2F$folderName".toUri()

private class OpenDocumentTreeWithHint : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(context: Context, input: Uri?): Intent {
        val intent = super.createIntent(context, input)
        input?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        return intent
    }
}

private fun createFolderInDocuments(context: Context, folderName: String) {
    val cleanName = folderName.toSafeFolderName()
    if (cleanName.isEmpty()) return

    val contentUri = MediaStore.Files.getContentUri("external")

    // MediaStore tidak mengisi DISPLAY_NAME untuk direktori, jadi folder dicari lewat berkas di dalamnya
    val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
    val selectionArgs = arrayOf("${Environment.DIRECTORY_DOCUMENTS}/$cleanName/%")

    try {
        context.contentResolver.query(
            contentUri,
            arrayOf(MediaStore.MediaColumns._ID),
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return
        }
    } catch (e: Exception) {
        context.logcat(LogPriority.WARN, e) { "Failed to check existing folder" }
    }

    try {
        // Membuat lalu menghapus berkas sementara memaksa sistem menyiapkan direktori fisiknya
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, ".nomedia")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/$cleanName/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        context.contentResolver.insert(contentUri, values)?.let {
            context.contentResolver.delete(it, null, null)
        }
    } catch (e: Exception) {
        context.logcat(LogPriority.WARN, e) { "Failed to prepare folder" }
    }
}
// KMK <--
