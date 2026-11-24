package eu.kanade.presentation.more.storage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.icerock.moko.resources.StringResource
import kotlinx.coroutines.delay
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.i18n.stringResource

// KMK -->

/** A long path such as /storage/emulated/0/Documents/Komikku is cut down to Documents/Komikku. */
internal fun String.toShortStoragePath(): String {
    if (isEmpty()) return this
    val marker = "0/"
    val index = indexOf(marker)
    return if (index >= 0) substring(index + marker.length) else substringAfterLast('/')
}

/**
 * Folder card shared by onboarding and the storage location sheet. The folder name is the main
 * content, while the label and location stay secondary so they do not compete.
 */
@Composable
internal fun StorageFolderCard(
    label: String,
    name: String,
    path: String,
    mode: String?,
    // Null means the folder name cannot be changed from here
    onStartEditing: (() -> Unit)?,
    isEditing: Boolean,
    onFolderNameChange: (String) -> Unit = {},
    onFinishEditing: () -> Unit = {},
    // False while the typed name equals the stored one, because there is nothing to apply
    canApply: Boolean = true,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        // A thin border keeps the card edge readable on themes whose container colors are close
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isEditing) {
                FolderNameField(
                    // The raw text while typing, so the field is never empty just because the
                    // sanitized form dropped a character the user is still writing
                    name = name,
                    canConfirm = name.isNotEmpty(),
                    isApplyEnabled = canApply,
                    onFolderNameChange = onFolderNameChange,
                    onFinishEditing = onFinishEditing,
                )
                return@Column
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // Status and the rename button share the name row, so the right side of the card
                    // holds one group and nothing dangles
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (mode != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = mode,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                            )
                        }
                        if (onStartEditing != null) {
                            IconButton(onClick = onStartEditing) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = stringResource(KMR.strings.settings_storage_rename),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            if (path.isNotEmpty()) {
                // The location is indented level with the name, so the eye reads the name first
                Text(
                    text = path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 36.dp, top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun FolderNameField(
    name: String,
    canConfirm: Boolean,
    isApplyEnabled: Boolean,
    onFolderNameChange: (String) -> Unit,
    onFinishEditing: () -> Unit,
) {
    OutlinedTextField(
        value = name,
        onValueChange = onFolderNameChange,
        label = { Text(stringResource(KMR.strings.onboarding_storage_folder_name_hint)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        // Enter on the keyboard finishes the input, just like the check button
        keyboardActions = KeyboardActions(
            onDone = { if (canConfirm) onFinishEditing() },
        ),
        trailingIcon = {
            IconButton(
                enabled = canConfirm && isApplyEnabled,
                onClick = onFinishEditing,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(MR.strings.action_ok),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

/**
 * One choice as a row, matching the app's settings rows: icon, title, and supporting text.
 * The background follows the parent, so the row does not stack like a card.
 */
@Composable
internal fun StorageActionRow(
    icon: ImageVector,
    title: String,
    supporting: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val supportingColor = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (enabled) MaterialTheme.colorScheme.primary else supportingColor,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = supportingColor,
            )
        }
    }
}

/** Notice kind: success (neutral) or warning (error colors). */
internal enum class NoticeKind { Success, Warning }

/**
 * Popup in the center of the screen for folder action results. It is the only floating surface,
 * so its shadow marks that it sits above the caller.
 */
@Composable
internal fun StorageNoticePopup(
    title: StringResource,
    message: StringResource,
    // Arguments for the message, usually the folder name in use
    formatArgs: List<Any> = emptyList(),
    kind: NoticeKind,
    // How long before the popup closes itself; 0 means no countdown
    durationMs: Long,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val warning = kind == NoticeKind.Warning
    val contentColor = if (warning) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val supportingColor = if (warning) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Seconds left before the popup closes itself; 0 means no countdown
    val totalSeconds = (durationMs / 1_000L).toInt()
    var secondsLeft by remember(durationMs) { mutableIntStateOf(totalSeconds) }
    LaunchedEffect(durationMs) {
        while (secondsLeft > 0) {
            delay(1_000L)
            secondsLeft -= 1
        }
    }

    // Its own dialog window: stays above the caller's dialog or bottom sheet
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = modifier
                    .widthIn(min = 280.dp, max = 320.dp)
                    // Announced by TalkBack without stealing focus from other content
                    .semantics { liveRegion = LiveRegionMode.Polite },
                shape = MaterialTheme.shapes.large,
                color = if (warning) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = contentColor,
                shadowElevation = 3.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = if (warning) {
                            Icons.Rounded.WarningAmber
                        } else {
                            Icons.Rounded.Check
                        },
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = if (warning) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                    Text(
                        text = stringResource(title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(message, *formatArgs.toTypedArray()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = supportingColor,
                        textAlign = TextAlign.Center,
                    )
                    // The time limit and the button share one row, so no text is left dangling
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        // Without a time limit, the OK button sits on the right
                        horizontalArrangement = if (totalSeconds > 0) {
                            Arrangement.SpaceBetween
                        } else {
                            Arrangement.End
                        },
                    ) {
                        if (totalSeconds > 0) {
                            Text(
                                text = stringResource(
                                    KMR.strings.settings_storage_notice_countdown,
                                    secondsLeft,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = supportingColor,
                                // The per-second count is not announced repeatedly by TalkBack
                                modifier = Modifier.clearAndSetSemantics { },
                            )
                        }
                        TextButton(
                            onClick = onConfirm,
                            // The button follows the container color so it stays readable on a warning popup
                            colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
                        ) {
                            Text(stringResource(MR.strings.action_ok))
                        }
                    }
                }
            }
        }
    }
}

// KMK <--
