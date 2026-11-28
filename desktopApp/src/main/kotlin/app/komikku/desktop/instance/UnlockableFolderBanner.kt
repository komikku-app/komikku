package app.komikku.desktop.instance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * ANSWER 013: above [content], a warning that the data folder cannot be locked ([DesktopSingleInstance.lockUnsupported]),
 * shown while [show] until it is dismissed. [dismissed] lives in the data folder's preferences (an app-state key, not
 * in backups), so it is remembered per data folder.
 */
@Composable
fun UnlockableFolderBanner(
    show: Boolean,
    dismissed: Preference<Boolean> = remember { unlockableFolderBannerDismissed(Injekt.get()) },
    content: @Composable () -> Unit,
) {
    val isDismissed by dismissed.collectAsState()
    // One layout whether the banner shows or not: dismissing it must not recreate the content (its navigation)
    Column(modifier = Modifier.fillMaxSize()) {
        if (show && !isDismissed) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(imageVector = Icons.Filled.Warning, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(KMR.strings.desktop_unlockable_data_folder),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { dismissed.set(true) }) {
                        Text(text = stringResource(MR.strings.action_ok))
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

fun unlockableFolderBannerDismissed(store: PreferenceStore): Preference<Boolean> =
    store.getBoolean(Preference.appStateKey("desktop_unlockable_folder_banner_dismissed"), false)
