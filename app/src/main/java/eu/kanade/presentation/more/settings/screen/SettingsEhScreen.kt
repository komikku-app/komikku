package eu.kanade.presentation.more.settings.screen

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.kanade.domain.source.interactor.ToggleIncognito
import eu.kanade.presentation.library.components.SyncFavoritesWarningDialog
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.system.toast
import exh.eh.EHentaiUpdateWorker
import exh.eh.EHentaiUpdateWorkerConstants
import exh.eh.EHentaiUpdaterStats
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.source.EH_PACKAGE
import exh.source.ExhPreferences
import exh.ui.login.EhLoginActivity
import exh.util.nullIfBlank
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.serialization.json.Json
import logcat.LogPriority
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_CHARGING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.DEVICE_ONLY_ON_WIFI
import tachiyomi.domain.manga.interactor.DeleteFavoriteEntries
import tachiyomi.domain.manga.interactor.GetExhFavoriteMangaWithMetadata
import tachiyomi.domain.manga.interactor.GetFlatMetadataById
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object SettingsEhScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsEhScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = SYMR.strings.pref_category_eh

    override fun isEnabled(): Boolean = Injekt.get<ExhPreferences>().isHentaiEnabled().get()

    @Composable
    fun Reconfigure(
        exhPreferences: ExhPreferences,
        openWarnConfigureDialogController: () -> Unit,
    ) {
        var initialLoadGuard by remember { mutableStateOf(false) }
        val useHentaiAtHome by exhPreferences.useHentaiAtHome().collectAsState()
        val useJapaneseTitle by exhPreferences.useJapaneseTitle().collectAsState()
        val useOriginalImages by exhPreferences.exhUseOriginalImages().collectAsState()
        val ehTagFilterValue by exhPreferences.ehTagFilterValue().collectAsState()
        val ehTagWatchingValue by exhPreferences.ehTagWatchingValue().collectAsState()
        val settingsLanguages by exhPreferences.exhSettingsLanguages().collectAsState()
        val enabledCategories by exhPreferences.exhEnabledCategories().collectAsState()
        val imageQuality by exhPreferences.imageQuality().collectAsState()
        DisposableEffect(
            useHentaiAtHome,
            useJapaneseTitle,
            useOriginalImages,
            ehTagFilterValue,
            ehTagWatchingValue,
            settingsLanguages,
            enabledCategories,
            imageQuality,
        ) {
            if (initialLoadGuard) {
                openWarnConfigureDialogController()
            }
            initialLoadGuard = true
            onDispose {}
        }
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val exhPreferences: ExhPreferences = remember { Injekt.get() }
        val getFlatMetadataById: GetFlatMetadataById = remember { Injekt.get() }
        val deleteFavoriteEntries: DeleteFavoriteEntries = remember { Injekt.get() }
        val getExhFavoriteMangaWithMetadata: GetExhFavoriteMangaWithMetadata = remember { Injekt.get() }
        val exhentaiEnabled by exhPreferences.enableExhentai().collectAsState()
        var runConfigureDialog by remember { mutableStateOf(false) }
        val openWarnConfigureDialogController = { runConfigureDialog = true }

        Reconfigure(exhPreferences, openWarnConfigureDialogController)

        ConfigureExhDialog(run = runConfigureDialog, onRunning = { runConfigureDialog = false })

        return listOf(
            // KMK -->
            Preference.PreferenceGroup(
                stringResource(MR.strings.source_settings),
                preferenceItems = persistentListOf(
                    ehIncognitoMode(exhPreferences),
                ),
            ),
            // KMK <--
            Preference.PreferenceGroup(
                stringResource(SYMR.strings.ehentai_prefs_account_settings),
                preferenceItems = persistentListOf(
                    getLoginPreference(exhPreferences, openWarnConfigureDialogController),
                    useHentaiAtHome(exhentaiEnabled, exhPreferences),
                    useJapaneseTitle(exhentaiEnabled, exhPreferences),
                    useOriginalImages(exhentaiEnabled, exhPreferences),
                    watchedTags(exhentaiEnabled),
                    tagFilterThreshold(exhentaiEnabled, exhPreferences),
                    tagWatchingThreshold(exhentaiEnabled, exhPreferences),
                    settingsLanguages(exhentaiEnabled, exhPreferences),
                    enabledCategories(exhentaiEnabled, exhPreferences),
                    watchedListDefaultState(exhentaiEnabled, exhPreferences),
                    imageQuality(exhentaiEnabled, exhPreferences),
                    enhancedEhentaiView(exhPreferences),
                ),
            ),
            Preference.PreferenceGroup(
                stringResource(SYMR.strings.favorites_sync),
                preferenceItems = persistentListOf(
                    readOnlySync(exhPreferences),
                    syncFavoriteNotes(),
                    lenientSync(exhPreferences),
                    forceSyncReset(deleteFavoriteEntries),
                ),
            ),
            Preference.PreferenceGroup(
                stringResource(SYMR.strings.gallery_update_checker),
                preferenceItems = persistentListOf(
                    updateCheckerFrequency(exhPreferences),
                    autoUpdateRequirements(exhPreferences),
                    updaterStatistics(
                        exhPreferences,
                        getExhFavoriteMangaWithMetadata,
                        getFlatMetadataById,
                    ),
                ),
            ),
        )
    }

    // KMK -->
    @Composable
    fun ehIncognitoMode(
        exhPreferences: ExhPreferences,
    ): Preference.PreferenceItem.SwitchPreference {
        return Preference.PreferenceItem.SwitchPreference(
            preference = exhPreferences.ehIncognitoMode(),
            title = stringResource(MR.strings.pref_incognito_mode),
            subtitle = stringResource(MR.strings.pref_incognito_mode_summary),
            onValueChanged = { newVal ->
                val toggleIncognito = Injekt.get<ToggleIncognito>()
                toggleIncognito.await(EH_PACKAGE, newVal)
                newVal
            },
        )
    }
    // KMK <--

    @Composable
    fun getLoginPreference(
        exhPreferences: ExhPreferences,
        openWarnConfigureDialogController: () -> Unit,
    ): Preference.PreferenceItem.SwitchPreference {
        val activityResultContract =
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == Activity.RESULT_OK) {
                    // Upload settings
                    openWarnConfigureDialogController()
                }
            }
        val context = LocalContext.current
        val value by exhPreferences.enableExhentai().collectAsState()
        return Preference.PreferenceItem.SwitchPreference(
            preference = exhPreferences.enableExhentai(),
            title = stringResource(SYMR.strings.enable_exhentai),
            subtitle = if (!value) {
                stringResource(SYMR.strings.requires_login)
            } else {
                null
            },
            onValueChanged = { newVal ->
                if (!newVal) {
                    exhPreferences.enableExhentai().set(false)
                    true
                } else {
                    activityResultContract.launch(EhLoginActivity.newIntent(context))
                    false
                }
            },
        )
    }

    @Composable
    fun useHentaiAtHome(
        exhentaiEnabled: Boolean,
        exhPreferences: ExhPreferences,
    ): Preference.PreferenceItem.ListPreference<Int> {
        return Preference.PreferenceItem.ListPreference(
            preference = exhPreferences.useHentaiAtHome(),
            entries = persistentMapOf(
                0 to stringResource(SYMR.strings.use_hentai_at_home_option_1),
                1 to stringResource(SYMR.strings.use_hentai_at_home_option_2),
            ),
            title = stringResource(SYMR.strings.use_hentai_at_home),
            subtitle = stringResource(SYMR.strings.use_hentai_at_home_summary),
            enabled = exhentaiEnabled,
        )
    }

    @Composable
    fun useJapaneseTitle(
        exhentaiEnabled: Boolean,
        exhPreferences: ExhPreferences,
    ): Preference.PreferenceItem.SwitchPreference {
        val value by exhPreferences.useJapaneseTitle().collectAsState()
        return Preference.PreferenceItem.SwitchPreference(
            preference = exhPreferences.useJapaneseTitle(),
            title = stringResource(SYMR.strings.show_japanese_titles),
            subtitle = if (value) {
                stringResource(SYMR.strings.show_japanese_titles_option_1)
            } else {
                stringResource(SYMR.strings.show_japanese_titles_option_2)
            },
            enabled = exhentaiEnabled,
        )
    }

    @Composable
    fun useOriginalImages(
        exhentaiEnabled: Boolean,
        exhPreferences: ExhPreferences,
    ): Preference.PreferenceItem.SwitchPreference {
        val value by exhPreferences.exhUseOriginalImages().collectAsState()
        return Preference.PreferenceItem.SwitchPreference(
            preference = exhPreferences.exhUseOriginalImages(),
            title = stringResource(SYMR.strings.use_original_images),
            subtitle = if (value) {
                stringResource(SYMR.strings.use_original_images_on)
            } else {
                stringResource(SYMR.strings.use_original_images_off)
            },
            enabled = exhentaiEnabled,
        )
    }

    @Composable
    fun watchedTags(exhentaiEnabled: Boolean): Preference.PreferenceItem.TextPreference {
        val context = LocalContext.current
        return Preference.PreferenceItem.TextPreference(
            title = stringResource(SYMR.strings.watched_tags),
            subtitle = stringResource(SYMR.strings.watched_tags_summary),
            enabled = exhentaiEnabled,
            onClick = {
                context.startActivity(
                    WebViewActivity.newIntent(
                        context,
                        url = "https://exhentai.org/mytags",
                        title = context.stringResource(SYMR.strings.watched_tags_exh),
                    ),
                )
            },
        )
    }

    @Composable
    fun TagThresholdDialog(
        onDismissRequest: () -> Unit,
        title: String,
        initialValue: Int,
        valueRange: IntRange,
        outsideRangeError: String,
        onValueChange: (Int) -> Unit,
    ) {
        var value by remember(initialValue) {
            mutableStateOf(initialValue.toString())
        }
        val isValid = remember(value) { value.toIntOrNull().let { it != null && it in valueRange } }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = {
                TextButton(
                    onClick = { onValueChange(value.toIntOrNull() ?: return@TextButton) },
                    enabled = isValid,
                ) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
            title = {
                Text(text = title)
            },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                ) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        maxLines = 1,
                        singleLine = true,
                        isError = !isValid,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = if (!isValid) {
                            { Icon(Icons.Outlined.Error, outsideRangeError) }
                        } else {
                            null
                        },
                    )
                    if (!isValid) {
                        Text(
                            text = outsideRangeError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            },
        )
    }

    @Composable
    fun tagFilterThreshold(
        exhentaiEnabled: Boolean,
        exhPreferences: ExhPreferences,
    ): Preference.PreferenceItem.TextPreference {
        val value by exhPreferences.ehTagFilterValue().collectAsState()
        var dialogOpen by remember { mutableStateOf(false) }
        if (dialogOpen) {
            TagThresholdDialog(
                onDismissRequest = { dialogOpen = false },
                title = stringResource(SYMR.strings.tag_filtering_threshold),
                initialValue = value,
                valueRange = -9999..0,
                outsideRangeError = stringResource(SYMR.strings.tag_filtering_threshhold_error),
                onValueChange = {
                    dialogOpen = false
                    exhPreferences.ehTagFilterValue().set(it)
                },
            )
        }
        return Preference.PreferenceItem.TextPreference(
            title = stringResource(SYMR.strings.tag_filtering_threshold),
            subtitle = stringResource(SYMR.strings.tag_filtering_threshhold_summary, value),
            enabled = exhentaiEnabled,
            onClick = {
                dialogOpen = true
            },
        )
    }

    @Composable
    fun tagWatchingThreshold(
        exhentaiEnabled: Boolean,
        exhPreferences: ExhPreferences,
    ): Preference.PreferenceItem.TextPreference {
        val value by exhPreferences.ehTagWatchingValue().collectAsState()
        var dialogOpen by remember { mutableStateOf(false) }
        if (dialogOpen) {
            TagThresholdDialog(
                onDismissRequest = { dialogOpen = false },
                title = stringResource(SYMR.strings.tag_watching_threshhold),
                initialValue = value,
                valueRange = 0..9999,
                outsideRangeError = stringResource(SYMR.strings.tag_watching_threshhold_error),
                onValueChange = {
                    dialogOpen = false
                    exhPreferences.ehTagWatchingValue().set(it)
                },
            )
        }
        return Preference.PreferenceItem.TextPreference(
            title = stringResource(SYMR.strings.tag_watching_threshhold),
            subtitle = stringResource(SYMR.strings.tag_watching_threshhold_summary, value),
            enabled = exhentaiEnabled,
            onClick = {
                dialogOpen = true
            },
        )
    }

    class LanguageDialogState(preference: String) {
        class RowState(original: ColumnState, translated: ColumnState, rewrite: ColumnState) {
            var original by mutableStateOf(original)
            var translated by mutableStateOf(translated)
            var rewrite by mutableStateOf(rewrite)

            fun toPreference() = "${original.value}*${translated.value}*${rewrite.value}"
        }
        enum class ColumnState(val value: String) {
            Unavailable("false"),
            Enabled("true"),
            Disabled("false"),
        }
        private fun String.toRowState(disableFirst: Boolean = false) = split("*")
            .map {
                if (it.toBoolean()) {
                    ColumnState.Enabled
                } else {
                    ColumnState.Disabled
                }
            }
            .let {
                if (disableFirst) {
                    RowState(ColumnState.Unavailable, it[1], it[2])
                } else {
                    RowState(it[0], it[1], it[2])
                }
            }

        val japanese: RowState
        val english: RowState
        val chinese: RowState
        val dutch: RowState
        val french: RowState
        val german: RowState
        val hungarian: RowState
        val italian: RowState
        val korean: RowState
        val polish: RowState
        val portuguese: RowState
        val russian: RowState
        val spanish: RowState
        val thai: RowState
        val vietnamese: RowState
        val notAvailable: RowState
        val other: RowState

        init {
            val settingsLanguages = preference.split("\n")
            japanese = settingsLanguages[0].toRowState(true)
            english = settingsLanguages[1].toRowState()
            chinese = settingsLanguages[2].toRowState()
            dutch = settingsLanguages[3].toRowState()
            french = settingsLanguages[4].toRowState()
            german = settingsLanguages[5].toRowState()
            hungarian = settingsLanguages[6].toRowState()
            italian = settingsLanguages[7].toRowState()
            korean = settingsLanguages[8].toRowState()
            polish = settingsLanguages[9].toRowState()
            portuguese = settingsLanguages[10].toRowState()
            russian = settingsLanguages[11].toRowState()
            spanish = settingsLanguages[12].toRowState()
            thai = settingsLanguages[13].toRowState()
            vietnamese = settingsLanguages[14].toRowState()
            notAvailable = settingsLanguages[15].toRowState()
            other = settingsLanguages[16].toRowState()
        }

        fun toPreference() = listOf(
            japanese,
            english,
            chinese,
            dutch,
            french,
            german,
            hungarian,
            italian,
            korean,
            polish,
            portuguese,
            russian,
            spanish,
            thai,
            vietnamese,
            notAvailable,
            other,
        ).joinToString("\n") { it.toPreference() }
    }

    @Composable
    fun LanguageDialogRowCheckbox(
        columnState: LanguageDialogState.ColumnState,
        onStateChange: (LanguageDialogState.ColumnState) -> Unit,
    ) {
        if (columnState != LanguageDialogState.ColumnState.Unavailable) {
            Checkbox(
                checked = columnState == LanguageDialogState.ColumnState.Enabled,
                onCheckedChange = {
                    if (it) {
                        onStateChange(LanguageDialogState.ColumnState.Enabled)
                    } else {
                        onStateChange(LanguageDialogState.ColumnState.Disabled)
                    }
                },
            )
        } else {
            Box(modifier = Modifier.size(48.dp))
        }
    }

    @Composable
    fun LanguageDialogRow(
        language: String,
        row: LanguageDialogState.RowState,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = language,
                modifier = Modifier
                    .padding(4.dp)
                    .width(80.dp),
                maxLines = 1,
            )
            LanguageDialogRowCheckbox(row.original, onStateChange = { row.original = it })
            LanguageDialogRowCheckbox(row.translated, onStateChange = { row.translated = it })
            LanguageDialogRowCheckbox(row.rewrite, onStateChange = { row.rewrite = it })
        }
    }

    @Composable
    fun LanguagesDialog(
        onDismissRequest: () -> Unit,
        initialValue: String,
        onValueChange: (String) -> Unit,
    ) {
        val state = remember(initialValue) { LanguageDialogState(initialValue) }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(SYMR.strings.language_filtering)) },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(stringResource(SYMR.strings.language_filtering_summary))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "Language", modifier = Modifier.padding(4.dp))
                        Text(text = "Original", modifier = Modifier.padding(4.dp))
                        Text(text = "Translated", modifier = Modifier.padding(4.dp))
                        Text(text = "Rewrite", modifier = Modifier.padding(4.dp))
                    }
                    LanguageDialogRow(language = "Japanese", row = state.japanese)
                    LanguageDialogRow(language = "English", row = state.english)
                    LanguageDialogRow(language = "Chinese", row = state.chinese)
                    LanguageDialogRow(language = "Dutch", row = state.dutch)
                    LanguageDialogRow(language = "French", row = state.french)
                    LanguageDialogRow(language = "German", row = state.german)
                    LanguageDialogRow(language = "Hungarian", row = state.hungarian)
                    LanguageDialogRow(language = "Italian", row = state.italian)
                    LanguageDialogRow(language = "Korean", row = state.korean)
                    LanguageDialogRow(language = "Polish", row = state.polish)
                    LanguageDialogRow(language = "Portuguese", row = state.portuguese)
                    LanguageDialogRow(language = "Russian", row = state.russian)
                    LanguageDialogRow(language = "Spanish", row = state.spanish)
                    LanguageDialogRow(language = "Thai", row = state.thai)
                    LanguageDialogRow(language = "Vietnamese", row = state.vietnamese)
                    LanguageDialogRow(language = "N/A", row = state.notAvailable)
                    LanguageDialogRow(language = "Other", row = state.other)
                }
            },
            confirmButton = {
                TextButton(onClick = { onValueChange(state.toPreference()) }) {
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
    fun settingsLanguages(
        exhentaiEnabled: Boolean,
        exhPreferences: ExhPreferences,
    ): Preference.PreferenceItem.TextPreference {
        val value by exhPreferences.exhSettingsLanguages().collectAsState()
        var dialogOpen by remember { mutableStateOf(false) }
        if (dialogOpen) {
            LanguagesDialog(
                onDismissRequest = { dialogOpen = false },
                initialValue = value,
                onValueChange = {
                    dialogOpen = false
                    exhPreferences.exhSettingsLanguages().set(it)
                },
            )
        }
        return Preference.PreferenceItem.TextPreference(
            title = stringResource(SYMR.strings.language_filtering),
            subtitle = stringResource(SYMR.strings.language_filtering_summary),
            enabled = exhentaiEnabled,
            onClick = {
                dialogOpen = true
            },
        )
    }

    class FrontPageCategoriesDialogState(
        preference: String,
    ) {
        private val enabledCategories = preference.split(",").map { !it.toBoolean() }
        var doujinshi by mutableStateOf(enabledCategories[0])
        var manga by mutableStateOf(enabledCategories[1])
        var artistCg by mutableStateOf(enabledCategories[2])
        var gameCg by mutableStateOf(enabledCategories[3])
        var western by mutableStateOf(enabledCategories[4])
        var nonH by mutableStateOf(enabledCategories[5])
        var imageSet by mutableStateOf(enabledCategories[6])
        var cosplay by mutableStateOf(enabledCategories[7])
        var asianPorn by mutableStateOf(enabledCategories[8])
        var misc by mutableStateOf(enabledCategories[9])

        fun toPreference() = listOf(
            doujinshi,
            manga,
            artistCg,
            gameCg,
            western,
            nonH,
            imageSet,
            cosplay,
            asianPorn,
            misc,
        ).joinToString(separator = ",") { (!it).toString() }
    }

    @Composable
    fun FrontPageCategoriesDialogRow(
        title: String,
        value: Boolean,
        onValueChange: (Boolean) -> Unit,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onValueChange(!value) }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title)
            Switch(checked = value, onCheckedChange = null)
        }
    }

    @Composable
    fun FrontPageCategoriesDialog(
        onDismissRequest: () -> Unit,
        initialValue: String,
        onValueChange: (String) -> Unit,
    ) {
        val state = remember(initialValue) { FrontPageCategoriesDialogState(initialValue) }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(SYMR.strings.frong_page_categories)) },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(stringResource(SYMR.strings.fromt_page_categories_summary))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "Category", modifier = Modifier.padding(4.dp))
                        Text(text = "Enabled", modifier = Modifier.padding(4.dp))
                    }
                    FrontPageCategoriesDialogRow(
                        title = "Doujinshi",
                        value = state.doujinshi,
                        onValueChange = { state.doujinshi = it },
                    )
                    FrontPageCategoriesDialogRow(
                        title = "Manga",
                        value = state.manga,
                        onValueChange = { state.manga = it },
                    )
                    FrontPageCategoriesDialogRow(
                        title = "Artist CG",
                        value = state.artistCg,
                        onValueChange = { state.artistCg = it },
                    )
                    FrontPageCategoriesDialogRow(
                        title = "Game CG",
                        value = state.gameCg,
                        onValueChange = { state.gameCg = it },
                    )
                    FrontPageCategoriesDialogRow(
                        title = "Western",
                        value = state.western,
                        onValueChange = { state.western = it },
                    )
                    FrontPageCategoriesDialogRow(
                        title = "Non-H",
                        value = state.nonH,
                        onValueChange = { state.nonH = it },
                    )
                    FrontPageCategoriesDialogRow(
                        title = "Image Set",
                        value = state.imageSet,
                        onValueChange = { state.imageSet = it },
                    )
                    FrontPageCategoriesDialogRow(
                        title = "Cosplay",
                        value = state.cosplay,
                        onValueChange = { state.cosplay = it },
                    )
                    FrontPageCategoriesDialogRow(
                        title = "Asian Porn",
                        value = state.asianPorn,
                        onValueChange = { state.asianPorn = it },
                    )
                    FrontPageCategoriesDialogRow(
                        title = "Misc",
                        value = state.misc,
                        onValueChange = { state.misc = it },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onValueChange(state.toPreference()) }) {
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
    fun enabledCategories(
        exhentaiEnabled: Boolean,
        exhPreferences: ExhPreferences,
    ): Preference.PreferenceItem.TextPreference {
        val value by exhPreferences.exhEnabledCategories().collectAsState()
        var dialogOpen by remember { mutableStateOf(false) }
        if (dialogOpen) {
            FrontPageCategoriesDialog(
                onDismissRequest = { dialogOpen = false },
                initialValue = value,
                onValueChange = {
                    dialogOpen = false
                    exhPreferences.exhEnabledCategories().set(it)
                },
            )
        }
        return Preference.PreferenceItem.TextPreference(
            title = stringResource(SYMR.strings.frong_page_categories),
            subtitle = stringResource(SYMR.strings.fromt_page_categories_summary),
            enabled = exhentaiEnabled,
            onClick = {
                dialogOpen = true
            },
        )
    }

    @Composable
    fun watchedListDefaultState(
        exhentaiEnabled: Boolean,
        exhPreferences: ExhPreferences,
    ): Preference.PreferenceItem.SwitchPreference {
        return Preference.PreferenceItem.SwitchPreference(
            preference = exhPreferences.exhWatchedListDefaultState(),
            title = stringResource(SYMR.strings.watched_list_default),
            subtitle = stringResource(SYMR.strings.watched_list_state_summary),
            enabled = exhentaiEnabled,
        )
    }

    @Composable
    fun imageQuality(
        exhentaiEnabled: Boolean,
        exhPreferences: ExhPreferences,
    ): Preference.PreferenceItem.ListPreference<String> {
        return Preference.PreferenceItem.ListPreference(
            preference = exhPreferences.imageQuality(),
            entries = persistentMapOf(
                "auto" to stringResource(SYMR.strings.eh_image_quality_auto),
                "ovrs_2400" to stringResource(SYMR.strings.eh_image_quality_2400),
                "ovrs_1600" to stringResource(SYMR.strings.eh_image_quality_1600),
                "high" to stringResource(SYMR.strings.eh_image_quality_1280),
                "med" to stringResource(SYMR.strings.eh_image_quality_980),
                "low" to stringResource(SYMR.strings.eh_image_quality_780),
            ),
            title = stringResource(SYMR.strings.eh_image_quality_summary),
            subtitle = stringResource(SYMR.strings.eh_image_quality),
            enabled = exhentaiEnabled,
        )
    }

    @Composable
    fun enhancedEhentaiView(exhPreferences: ExhPreferences): Preference.PreferenceItem.SwitchPreference {
        return Preference.PreferenceItem.SwitchPreference(
            preference = exhPreferences.enhancedEHentaiView(),
            title = stringResource(SYMR.strings.pref_enhanced_e_hentai_view),
            subtitle = stringResource(SYMR.strings.pref_enhanced_e_hentai_view_summary),
        )
    }

    @Composable
    fun readOnlySync(exhPreferences: ExhPreferences): Preference.PreferenceItem.SwitchPreference {
        return Preference.PreferenceItem.SwitchPreference(
            preference = exhPreferences.exhReadOnlySync(),
            title = stringResource(SYMR.strings.disable_favorites_uploading),
            subtitle = stringResource(SYMR.strings.disable_favorites_uploading_summary),
        )
    }

    @Composable
    fun syncFavoriteNotes(): Preference.PreferenceItem.TextPreference {
        var dialogOpen by remember { mutableStateOf(false) }
        if (dialogOpen) {
            SyncFavoritesWarningDialog(
                onDismissRequest = { dialogOpen = false },
                onAccept = { dialogOpen = false },
            )
        }
        return Preference.PreferenceItem.TextPreference(
            title = stringResource(SYMR.strings.show_favorite_sync_notes),
            subtitle = stringResource(SYMR.strings.show_favorite_sync_notes_summary),
            onClick = { dialogOpen = true },
        )
    }

    @Composable
    fun lenientSync(exhPreferences: ExhPreferences): Preference.PreferenceItem.SwitchPreference {
        return Preference.PreferenceItem.SwitchPreference(
            preference = exhPreferences.exhLenientSync(),
            title = stringResource(SYMR.strings.ignore_sync_errors),
            subtitle = stringResource(SYMR.strings.ignore_sync_errors_summary),
        )
    }

    @Composable
    fun SyncResetDialog(
        onDismissRequest: () -> Unit,
        onStartReset: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(SYMR.strings.favorites_sync_reset))
            },
            text = {
                Text(text = stringResource(SYMR.strings.favorites_sync_reset_message))
            },
            confirmButton = {
                TextButton(onClick = onStartReset) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_cancel))
                }
            },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        )
    }

    @Composable
    fun forceSyncReset(deleteFavoriteEntries: DeleteFavoriteEntries): Preference.PreferenceItem.TextPreference {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var dialogOpen by remember { mutableStateOf(false) }
        if (dialogOpen) {
            SyncResetDialog(
                onDismissRequest = { dialogOpen = false },
                onStartReset = {
                    dialogOpen = false
                    scope.launchNonCancellable {
                        try {
                            deleteFavoriteEntries.await()
                            withUIContext {
                                context.toast(context.stringResource(SYMR.strings.sync_state_reset), Toast.LENGTH_LONG)
                            }
                        } catch (e: Exception) {
                            this@SettingsEhScreen.logcat(LogPriority.ERROR, e)
                        }
                    }
                },
            )
        }
        return Preference.PreferenceItem.TextPreference(
            title = stringResource(SYMR.strings.force_sync_state_reset),
            subtitle = stringResource(SYMR.strings.force_sync_state_reset_summary),
            onClick = {
                dialogOpen = true
            },
        )
    }

    @Composable
    fun updateCheckerFrequency(
        exhPreferences: ExhPreferences,
    ): Preference.PreferenceItem.ListPreference<Int> {
        val value by exhPreferences.exhAutoUpdateFrequency().collectAsState()
        val context = LocalContext.current
        return Preference.PreferenceItem.ListPreference(
            preference = exhPreferences.exhAutoUpdateFrequency(),
            entries = persistentMapOf(
                0 to stringResource(SYMR.strings.time_between_batches_never),
                1 to stringResource(SYMR.strings.time_between_batches_1_hour),
                2 to stringResource(SYMR.strings.time_between_batches_2_hours),
                3 to stringResource(SYMR.strings.time_between_batches_3_hours),
                6 to stringResource(SYMR.strings.time_between_batches_6_hours),
                12 to stringResource(SYMR.strings.time_between_batches_12_hours),
                24 to stringResource(SYMR.strings.time_between_batches_24_hours),
                48 to stringResource(SYMR.strings.time_between_batches_48_hours),
            ),
            title = stringResource(SYMR.strings.time_between_batches),
            subtitle = if (value == 0) {
                stringResource(SYMR.strings.time_between_batches_summary_1, stringResource(MR.strings.app_name))
            } else {
                stringResource(
                    SYMR.strings.time_between_batches_summary_2,
                    stringResource(MR.strings.app_name),
                    value,
                    EHentaiUpdateWorkerConstants.UPDATES_PER_ITERATION,
                )
            },
            onValueChanged = { interval ->
                EHentaiUpdateWorker.scheduleBackground(context, prefInterval = interval)
                true
            },
        )
    }

    @Composable
    fun autoUpdateRequirements(
        exhPreferences: ExhPreferences,
    ): Preference.PreferenceItem.MultiSelectListPreference {
        val value by exhPreferences.exhAutoUpdateRequirements().collectAsState()
        val context = LocalContext.current
        return Preference.PreferenceItem.MultiSelectListPreference(
            preference = exhPreferences.exhAutoUpdateRequirements(),
            entries = persistentMapOf(
                DEVICE_ONLY_ON_WIFI to stringResource(MR.strings.connected_to_wifi),
                DEVICE_CHARGING to stringResource(MR.strings.charging),
            ),
            title = stringResource(SYMR.strings.auto_update_restrictions),
            subtitle = remember(value) {
                context.stringResource(
                    MR.strings.restrictions,
                    value.sorted()
                        .map {
                            when (it) {
                                DEVICE_ONLY_ON_WIFI -> context.stringResource(MR.strings.connected_to_wifi)
                                DEVICE_CHARGING -> context.stringResource(MR.strings.charging)
                                else -> it
                            }
                        }
                        .ifEmpty {
                            listOf(context.stringResource(MR.strings.none))
                        }
                        .joinToString(),
                )
            },
            onValueChanged = { restrictions ->
                EHentaiUpdateWorker.scheduleBackground(context, prefRestrictions = restrictions)
                true
            },
        )
    }

    @Composable
    fun UpdaterStatisticsLoadingDialog() {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            Surface(
                modifier = Modifier.sizeIn(minWidth = 280.dp, maxWidth = 560.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = stringResource(SYMR.strings.gallery_updater_statistics_collection),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(40.dp),
                )
            }
        }
    }

    @Composable
    fun UpdaterStatisticsDialog(
        onDismissRequest: () -> Unit,
        updateInfo: String,
    ) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(SYMR.strings.gallery_updater_statistics))
            },
            text = {
                Text(text = updateInfo)
            },
            confirmButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(text = stringResource(MR.strings.action_ok))
                }
            },
        )
    }

    private fun getRelativeTimeFromNow(then: Duration): RelativeTime {
        val now = System.currentTimeMillis().milliseconds
        var period: Duration = now - then
        val relativeTime = RelativeTime()
        while (period > 0.milliseconds) {
            when {
                period >= 365.days -> {
                    (period.inWholeDays / 365).let {
                        relativeTime.years = it
                        period -= (it * 365).days
                    }
                    continue
                }
                period >= 30.days -> {
                    (period.inWholeDays / 30).let {
                        relativeTime.months = it
                        period -= (it * 30).days
                    }
                }
                period >= 7.days -> {
                    (period.inWholeDays / 7).let {
                        relativeTime.weeks = it
                        period -= (it * 7).days
                    }
                }
                period >= 1.days -> {
                    period.inWholeDays.let {
                        relativeTime.days = it
                        period -= it.days
                    }
                }
                period >= 1.hours -> {
                    period.inWholeHours.let {
                        relativeTime.hours = it
                        period -= it.hours
                    }
                }
                period >= 1.minutes -> {
                    period.inWholeMinutes.let {
                        relativeTime.minutes = it
                        period -= it.minutes
                    }
                }
                period >= 1.seconds -> {
                    period.inWholeSeconds.let {
                        relativeTime.seconds = it
                        period -= it.seconds
                    }
                }
                period >= 1.milliseconds -> {
                    period.inWholeMilliseconds.let {
                        relativeTime.milliseconds = it
                    }
                    period = Duration.ZERO
                }
            }
        }
        return relativeTime
    }

    private fun getRelativeTimeString(relativeTime: RelativeTime, context: Context): String {
        return relativeTime.years?.let { context.pluralStringResource(SYMR.plurals.humanize_year, it.toInt(), it) }
            ?: relativeTime.months?.let {
                context.pluralStringResource(SYMR.plurals.humanize_month, it.toInt(), it)
            }
            ?: relativeTime.weeks?.let { context.pluralStringResource(SYMR.plurals.humanize_week, it.toInt(), it) }
            ?: relativeTime.days?.let { context.pluralStringResource(SYMR.plurals.humanize_day, it.toInt(), it) }
            ?: relativeTime.hours?.let { context.pluralStringResource(SYMR.plurals.humanize_hour, it.toInt(), it) }
            ?: relativeTime.minutes?.let {
                context.pluralStringResource(SYMR.plurals.humanize_minute, it.toInt(), it)
            }
            ?: relativeTime.seconds?.let {
                context.pluralStringResource(SYMR.plurals.humanize_second, it.toInt(), it)
            }
            ?: context.stringResource(SYMR.strings.humanize_fallback)
    }

    data class RelativeTime(
        var years: Long? = null,
        var months: Long? = null,
        var weeks: Long? = null,
        var days: Long? = null,
        var hours: Long? = null,
        var minutes: Long? = null,
        var seconds: Long? = null,
        var milliseconds: Long? = null,
    )

    @Composable
    fun updaterStatistics(
        exhPreferences: ExhPreferences,
        getExhFavoriteMangaWithMetadata: GetExhFavoriteMangaWithMetadata,
        getFlatMetadataById: GetFlatMetadataById,
    ): Preference.PreferenceItem.TextPreference {
        val context = LocalContext.current
        var dialogOpen by remember { mutableStateOf(false) }
        if (dialogOpen) {
            val updateInfo by produceState<String?>(null) {
                value = withIOContext {
                    try {
                        val stats =
                            exhPreferences.exhAutoUpdateStats().get().nullIfBlank()?.let {
                                Json.decodeFromString<EHentaiUpdaterStats>(it)
                            }

                        val statsText = if (stats != null) {
                            context.stringResource(
                                SYMR.strings.gallery_updater_stats_text,
                                getRelativeTimeString(getRelativeTimeFromNow(stats.startTime.milliseconds), context),
                                stats.updateCount,
                                stats.possibleUpdates,
                            )
                        } else {
                            context.stringResource(SYMR.strings.gallery_updater_not_ran_yet)
                        }

                        val allMeta = getExhFavoriteMangaWithMetadata.await()
                            .mapNotNull {
                                getFlatMetadataById.await(it.id)
                                    ?.raise<EHentaiSearchMetadata>()
                            }

                        fun metaInRelativeDuration(duration: Duration): Int {
                            val durationMs = duration.inWholeMilliseconds
                            return allMeta.asSequence().filter {
                                System.currentTimeMillis() - it.lastUpdateCheck < durationMs
                            }.count()
                        }

                        statsText + "\n\n" + context.stringResource(
                            SYMR.strings.gallery_updater_stats_time,
                            metaInRelativeDuration(1.hours),
                            metaInRelativeDuration(6.hours),
                            metaInRelativeDuration(12.hours),
                            metaInRelativeDuration(1.days),
                            metaInRelativeDuration(2.days),
                            metaInRelativeDuration(7.days),
                            metaInRelativeDuration(30.days),
                            metaInRelativeDuration(365.days),
                        )
                    } catch (e: Exception) {
                        logcat(LogPriority.ERROR, e) { "Error loading gallery update info" }
                        ""
                    }
                }
            }
            if (updateInfo == null) {
                UpdaterStatisticsLoadingDialog()
            } else {
                UpdaterStatisticsDialog(
                    onDismissRequest = { dialogOpen = false },
                    updateInfo = updateInfo.orEmpty(),
                )
            }
        }
        return Preference.PreferenceItem.TextPreference(
            title = stringResource(SYMR.strings.show_updater_statistics),
            onClick = {
                dialogOpen = true
            },
        )
    }
}
