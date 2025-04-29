package eu.kanade.presentation.more.settings.screen

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.doAfterTextChanged
import dev.icerock.moko.resources.StringResource
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.theme.colorscheme.AndroidViewColorScheme
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.databinding.DialogTrackingLoginBinding
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsTrackingScreen : SearchableSettings {
    private fun readResolve(): Any = SettingsTrackingScreen

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = MR.strings.pref_category_tracking

    @Composable
    override fun RowScope.AppBarAction() {
        val uriHandler = LocalUriHandler.current
        IconButton(onClick = { uriHandler.openUri("https://komikku-app.github.io/docs/guides/tracking") }) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = stringResource(MR.strings.tracking_guide),
            )
        }
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val context = LocalContext.current
        val trackPreferences = remember { Injekt.get<TrackPreferences>() }
        val trackerManager = remember { Injekt.get<TrackerManager>() }
        val sourceManager = remember { Injekt.get<SourceManager>() }

        var dialog by remember { mutableStateOf<Any?>(null) }
        dialog?.run {
            when (this) {
                is LoginDialog -> {
                    TrackingLoginDialog(
                        tracker = tracker,
                        uNameStringRes = uNameStringRes,
                        onDismissRequest = { dialog = null },
                    )
                }
                is LogoutDialog -> {
                    TrackingLogoutDialog(
                        tracker = tracker,
                        onDismissRequest = { dialog = null },
                    )
                }
            }
        }

        val enhancedTrackers = trackerManager.trackers
            .filter { it is EnhancedTracker }
            .partition { service ->
                val acceptedSources = (service as EnhancedTracker).getAcceptedSources()
                sourceManager.getCatalogueSources().any { it::class.qualifiedName in acceptedSources }
            }
        var enhancedTrackerInfo = stringResource(MR.strings.enhanced_tracking_info)
        if (enhancedTrackers.second.isNotEmpty()) {
            val missingSourcesInfo = stringResource(
                MR.strings.enhanced_services_not_installed,
                enhancedTrackers.second.joinToString { it.name },
            )
            enhancedTrackerInfo += "\n\n$missingSourcesInfo"
        }

        return listOf(
            Preference.PreferenceItem.SwitchPreference(
                preference = trackPreferences.autoUpdateTrack(),
                title = stringResource(MR.strings.pref_auto_update_manga_sync),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = trackPreferences.autoUpdateTrackOnMarkRead(),
                entries = AutoTrackState.entries
                    .associateWith { stringResource(it.titleRes) }
                    .toPersistentMap(),
                title = stringResource(MR.strings.pref_auto_update_manga_on_mark_read),
            ),
            // KMK -->
            Preference.PreferenceItem.SwitchPreference(
                preference = trackPreferences.autoSyncProgressFromTrackers(),
                title = stringResource(KMR.strings.pref_auto_sync_progress_from_trackers),
            ),
            // KMK <--
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.services),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.myAnimeList,
                        login = { context.openInBrowser(MyAnimeListApi.authUrl(), forceDefaultBrowser = true) },
                        logout = { dialog = LogoutDialog(trackerManager.myAnimeList) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.aniList,
                        login = { context.openInBrowser(AnilistApi.authUrl(), forceDefaultBrowser = true) },
                        logout = { dialog = LogoutDialog(trackerManager.aniList) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.kitsu,
                        login = { dialog = LoginDialog(trackerManager.kitsu, MR.strings.email) },
                        logout = { dialog = LogoutDialog(trackerManager.kitsu) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.mangaUpdates,
                        login = { dialog = LoginDialog(trackerManager.mangaUpdates, MR.strings.username) },
                        logout = { dialog = LogoutDialog(trackerManager.mangaUpdates) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.shikimori,
                        login = { context.openInBrowser(ShikimoriApi.authUrl(), forceDefaultBrowser = true) },
                        logout = { dialog = LogoutDialog(trackerManager.shikimori) },
                    ),
                    Preference.PreferenceItem.TrackerPreference(
                        tracker = trackerManager.bangumi,
                        login = { context.openInBrowser(BangumiApi.authUrl(), forceDefaultBrowser = true) },
                        logout = { dialog = LogoutDialog(trackerManager.bangumi) },
                    ),
                    Preference.PreferenceItem.InfoPreference(stringResource(MR.strings.tracking_info)),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(MR.strings.enhanced_services),
                preferenceItems = (
                    enhancedTrackers.first
                        .map { service ->
                            Preference.PreferenceItem.TrackerPreference(
                                tracker = service,
                                login = { (service as EnhancedTracker).loginNoop() },
                                logout = service::logout,
                            )
                        } + listOf(Preference.PreferenceItem.InfoPreference(enhancedTrackerInfo))
                    ).toImmutableList(),
            ),
        )
    }

    @Composable
    private fun TrackingLoginDialog(
        tracker: Tracker,
        uNameStringRes: StringResource,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        // KMK -->
        val usernameHint = stringResource(uNameStringRes)

        var username by remember { mutableStateOf(tracker.getUsername()) }
        var password by remember { mutableStateOf(tracker.getPassword()) }
        var processing by remember { mutableStateOf(false) }
        var inputError by remember { mutableStateOf(false) }
        val colorScheme = AndroidViewColorScheme(MaterialTheme.colorScheme)

        val density = LocalDensity.current
        var measuredHeightDp by remember { mutableStateOf(0.dp) }
        // KMK <--

        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(MR.strings.login_title, tracker.name),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismissRequest) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(MR.strings.action_close),
                        )
                    }
                }
            },
            text = {
                // KMK -->
                if (processing) {
                    LoadingScreen(
                        modifier = Modifier.heightIn(max = measuredHeightDp),
                    )
                } else {
                    AndroidView(
                        factory = { factoryContext ->
                            val binding = DialogTrackingLoginBinding.inflate(LayoutInflater.from(factoryContext))

                            // Measure with UNSPECIFIED height and AT_MOST width (e.g., screen width, or a large value)
                            // Using a fixed large value for simplicity, adjust if needed
                            val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST)
                            val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                            binding.root.measure(widthMeasureSpec, heightMeasureSpec)
                            with(density) { measuredHeightDp = binding.root.measuredHeight.toDp() }

                            val usernameInputLayout = binding.usernameInputLayout
                            val usernameEditText = binding.usernameEditText
                            val passwordInputLayout = binding.passwordInputLayout
                            val passwordEditText = binding.passwordEditText

                            listOf(usernameEditText, passwordEditText).forEach {
                                it.setTextColor(colorScheme.textColor)
                                it.highlightColor = colorScheme.textHighlightColor

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    it.textSelectHandle?.let { drawable ->
                                        drawable.setTint(colorScheme.primary)
                                        it.setTextSelectHandle(drawable)
                                    }
                                    it.textSelectHandleLeft?.let { drawable ->
                                        drawable.setTint(colorScheme.primary)
                                        it.setTextSelectHandleLeft(drawable)
                                    }
                                    it.textSelectHandleRight?.let { drawable ->
                                        drawable.setTint(colorScheme.primary)
                                        it.setTextSelectHandleRight(drawable)
                                    }
                                }
                            }

                            usernameInputLayout.hint = usernameHint
                            usernameEditText.setText(username)
                            passwordEditText.setText(password)

                            fun updateViewErrorState(isError: Boolean) {
                                val (strokeColorFocused, strokeColorDefault, hintColor, cursorColor) = if (isError) {
                                    arrayOf(
                                        colorScheme.error,
                                        colorScheme.error,
                                        colorScheme.error,
                                        colorScheme.error,
                                    )
                                } else {
                                    arrayOf(
                                        colorScheme.primary,
                                        colorScheme.onSurfaceVariant,
                                        colorScheme.primary,
                                        colorScheme.primary,
                                    )
                                }

                                val boxStrokeColorStateList = ColorStateList(
                                    arrayOf(
                                        intArrayOf(android.R.attr.state_focused),
                                        intArrayOf(), // Default state
                                    ),
                                    intArrayOf(
                                        strokeColorFocused,
                                        strokeColorDefault,
                                    ),
                                )
                                val hintTextColorStateList = ColorStateList.valueOf(hintColor)
                                val endIconTintList = ColorStateList.valueOf(colorScheme.onSurfaceVariant)
                                val cursorColorStateList = ColorStateList.valueOf(cursorColor)

                                listOf(usernameInputLayout, passwordInputLayout).forEach {
                                    it.setBoxStrokeColorStateList(boxStrokeColorStateList)
                                    it.hintTextColor = hintTextColorStateList
                                    it.setEndIconTintList(endIconTintList)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        it.cursorColor = cursorColorStateList
                                    }
                                }
                            }

                            // Clear errors when text changes & update Compose state
                            usernameEditText.doAfterTextChanged {
                                username = it?.toString() ?: ""
                                updateViewErrorState(false)
                            }
                            passwordEditText.doAfterTextChanged {
                                password = it?.toString() ?: ""
                                updateViewErrorState(false)
                            }

                            // Set the view as tag for the update lambda
                            binding.root.tag = ::updateViewErrorState
                            binding.root
                        },
                        update = { view ->
                            // Retrieve the function from tag and call it with the current error state
                            @Suppress("UNCHECKED_CAST")
                            val updateErrorState = view.tag as? (Boolean) -> Unit
                            updateErrorState?.invoke(inputError)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // KMK <--
            },
            confirmButton = {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !processing && username.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        // KMK -->
                        processing = true
                        inputError = false // Clear previous error before check
                        // KMK <--
                        scope.launchIO {
                            val result = checkLogin(
                                context = context,
                                tracker = tracker,
                                username = username,
                                password = password,
                            )
                            inputError = !result
                            if (result) onDismissRequest()
                            processing = false
                        }
                    },
                ) {
                    val id = if (processing) MR.strings.loading else MR.strings.login
                    Text(text = stringResource(id))
                }
            },
        )
    }

    private suspend fun checkLogin(
        context: Context,
        tracker: Tracker,
        username: String,
        password: String,
    ): Boolean {
        return try {
            tracker.login(username, password)
            withUIContext { context.toast(MR.strings.login_success) }
            true
        } catch (e: Throwable) {
            tracker.logout()
            withUIContext { context.toast(e.message.toString()) }
            false
        }
    }

    @Composable
    private fun TrackingLogoutDialog(
        tracker: Tracker,
        onDismissRequest: () -> Unit,
    ) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(
                    text = stringResource(MR.strings.logout_title, tracker.name),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall)) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = onDismissRequest,
                    ) {
                        Text(text = stringResource(MR.strings.action_cancel))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            tracker.logout()
                            onDismissRequest()
                            context.toast(MR.strings.logout_success)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Text(text = stringResource(MR.strings.logout))
                    }
                }
            },
        )
    }
}

private data class LoginDialog(
    val tracker: Tracker,
    val uNameStringRes: StringResource,
)

private data class LogoutDialog(
    val tracker: Tracker,
)
