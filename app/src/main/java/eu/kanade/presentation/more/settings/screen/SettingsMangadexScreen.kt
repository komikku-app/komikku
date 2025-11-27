package eu.kanade.presentation.more.settings.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PeopleAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.widget.BasePreferenceWidget
import eu.kanade.presentation.more.settings.widget.PrefsHorizontalPadding
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.library.LibraryUpdateJob
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import exh.md.utils.MdConstants
import exh.md.utils.MdUtil
import kotlinx.collections.immutable.toImmutableMap
import logcat.LogPriority
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.UnsortedPreferences
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import tachiyomi.presentation.core.util.secondaryItemAlpha
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsMangadexScreen : SearchableSettings {
    @Suppress("unused")
    private fun readResolve(): Any = SettingsMangadexScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = SYMR.strings.pref_category_mangadex

    override fun isEnabled(): Boolean = MdUtil.getEnabledMangaDexs(Injekt.get()).isNotEmpty()

    @Composable
    override fun getPreferences(): List<Preference> {
        val sourcePreferences: SourcePreferences = remember { Injekt.get() }
        val unsortedPreferences: UnsortedPreferences = remember { Injekt.get() }
        val trackPreferences: TrackPreferences = remember { Injekt.get() }
        val mdex = remember { MdUtil.getEnabledMangaDex(unsortedPreferences, sourcePreferences) } ?: return emptyList()

        return listOf(
            loginPreference(mdex, trackPreferences),
            preferredMangaDexId(unsortedPreferences, sourcePreferences),
            syncMangaDexIntoThis(unsortedPreferences),
            syncLibraryToMangaDex(),
        )
    }

    @Composable
    fun LogoutDialog(
        onDismissRequest: () -> Unit,
        onLogoutRequest: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(MR.strings.logout))
            },
            confirmButton = {
                TextButton(onClick = onLogoutRequest) {
                    Text(text = stringResource(MR.strings.logout))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    @Composable
    fun loginPreference(
        mdex: MangaDex,
        trackPreferences: TrackPreferences,
    ): Preference.PreferenceItem.CustomPreference {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val loggedIn by remember { trackPreferences.trackToken(mdex.mdList) }.collectAsState()
        var logoutDialogOpen by remember { mutableStateOf(false) }
        if (logoutDialogOpen) {
            LogoutDialog(
                onDismissRequest = { logoutDialogOpen = false },
                onLogoutRequest = {
                    logoutDialogOpen = false
                    scope.launchIO {
                        try {
                            if (mdex.logout()) {
                                withUIContext {
                                    context.toast(MR.strings.logout_success)
                                }
                            } else {
                                withUIContext {
                                    context.toast(MR.strings.unknown_error)
                                }
                            }
                        } catch (e: Exception) {
                            logcat(LogPriority.ERROR, e) { "Logout error" }
                            withUIContext {
                                context.toast(MR.strings.unknown_error)
                            }
                        }
                    }
                },
            )
        }
        return Preference.PreferenceItem.CustomPreference(
            title = mdex.name + " Login",
            content = {
                BasePreferenceWidget(
                    title = mdex.name + " Login",
                    widget = {
                        Icon(
                            imageVector = Icons.Outlined.PeopleAlt,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 12.dp, end = PrefsHorizontalPadding)
                                .secondaryItemAlpha(),
                            tint = if (loggedIn.isNotEmpty()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    subcomponent = null,
                    onClick = if (loggedIn.isNotEmpty()) {
                        {
                            logoutDialogOpen = true
                        }
                    } else {
                        {
                            context.openInBrowser(
                                MdConstants.Login.authUrl(MdUtil.getPkceChallengeCode()),
                                forceDefaultBrowser = true,
                            )
                        }
                    },
                )
            },
        )
    }

    @Composable
    fun preferredMangaDexId(
        unsortedPreferences: UnsortedPreferences,
        sourcePreferences: SourcePreferences,
    ): Preference.PreferenceItem.ListPreference<String> {
        return Preference.PreferenceItem.ListPreference(
            preference = unsortedPreferences.preferredMangaDexId(),
            entries = MdUtil.getEnabledMangaDexs(sourcePreferences)
                .associate { it.id.toString() to it.toString() }
                .toImmutableMap(),
            title = stringResource(SYMR.strings.mangadex_preffered_source),
            subtitle = stringResource(SYMR.strings.mangadex_preffered_source_summary),
        )
    }

    @Composable
    fun SyncMangaDexDialog(
        onDismissRequest: () -> Unit,
        onSelectionConfirmed: (List<String>) -> Unit,
    ) {
        val resources = LocalResources.current
        val items = remember {
            resources.getStringArray(R.array.md_follows_options)
                .drop(1)
        }
        val selection = remember {
            List(items.size) { index ->
                index == 0 || index == 5
            }.toMutableStateList()
        }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(SYMR.strings.mangadex_sync_follows_to_library))
            },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    items.forEachIndexed { index, followOption ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val checked = selection.getOrNull(index) ?: false
                                    selection[index] = !checked
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = selection.getOrNull(index) ?: false,
                                onCheckedChange = null,
                            )

                            Text(
                                text = followOption,
                                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onSelectionConfirmed(items.filterIndexed { index, _ -> selection[index] }) }) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
        )
    }

    @Composable
    fun syncMangaDexIntoThis(unsortedPreferences: UnsortedPreferences): Preference.PreferenceItem.TextPreference {
        val context = LocalContext.current
        var dialogOpen by remember { mutableStateOf(false) }
        if (dialogOpen) {
            SyncMangaDexDialog(
                onDismissRequest = { dialogOpen = false },
                onSelectionConfirmed = { items ->
                    dialogOpen = false
                    unsortedPreferences.mangadexSyncToLibraryIndexes().set(
                        List(items.size) { index -> (index + 1).toString() }.toSet(),
                    )
                    LibraryUpdateJob.startNow(
                        context,
                        target = LibraryUpdateJob.Target.SYNC_FOLLOWS,
                    )
                },
            )
        }
        return Preference.PreferenceItem.TextPreference(
            title = stringResource(SYMR.strings.mangadex_sync_follows_to_library),
            subtitle = stringResource(SYMR.strings.mangadex_sync_follows_to_library_summary),
            onClick = { dialogOpen = true },
        )
    }

    @Composable
    fun syncLibraryToMangaDex(): Preference.PreferenceItem.TextPreference {
        val context = LocalContext.current
        return Preference.PreferenceItem.TextPreference(
            title = stringResource(SYMR.strings.mangadex_push_favorites_to_mangadex),
            subtitle = stringResource(SYMR.strings.mangadex_push_favorites_to_mangadex_summary),
            onClick = {
                LibraryUpdateJob.startNow(
                    context,
                    target = LibraryUpdateJob.Target.PUSH_FAVORITES,
                )
            },
        )
    }
}
