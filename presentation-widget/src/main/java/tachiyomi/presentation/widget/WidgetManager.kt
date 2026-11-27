package tachiyomi.presentation.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.LifecycleCoroutineScope
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.updates.interactor.GetUpdates
import tachiyomi.domain.vault.VaultSession

class WidgetManager(
    private val getUpdates: GetUpdates,
    private val securityPreferences: SecurityPreferences,
) {

    fun Context.init(scope: LifecycleCoroutineScope) {
        // KMK -->
        // Force the widgets into their locked state while the private vault is active.
        if (VaultSession.isActive) {
            scope.launch(Dispatchers.Default) {
                try {
                    UpdatesGridGlanceWidget().updateAll(this@init)
                    UpdatesGridCoverScreenGlanceWidget().updateAll(this@init)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to update widget" }
                }
            }
            return
        }
        // KMK <--

        combine(
            getUpdates.subscribe(read = false, after = BaseUpdatesGridGlanceWidget.DateLimit.toEpochMilli()),
            securityPreferences.useAuthenticator().changes(),
            transform = { a, b -> a to b },
        )
            .distinctUntilChanged { old, new ->
                old.second == new.second &&
                    old.first.map { it.chapterId }.toSet() == new.first.map { it.chapterId }.toSet()
            }
            .onEach {
                try {
                    UpdatesGridGlanceWidget().updateAll(this)
                    UpdatesGridCoverScreenGlanceWidget().updateAll(this)
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, e) { "Failed to update widget" }
                }
            }
            .flowOn(Dispatchers.Default)
            .launchIn(scope)
    }
}
