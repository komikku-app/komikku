package eu.kanade.tachiyomi.ui.browse.migration.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.domain.chapter.interactor.SyncChaptersWithSource
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.manga.model.hasCustomCover
import eu.kanade.domain.manga.model.toSManga
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.ui.browse.migration.MigrationFlags
import exh.log.LogLevel
import exh.log.xLog
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.history.interactor.GetHistory
import tachiyomi.domain.history.interactor.UpsertHistory
import tachiyomi.domain.history.model.HistoryUpdate
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.interactor.InsertTrack
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.time.Instant

@Composable
internal fun MigrateDialog(
    oldManga: Manga,
    newManga: Manga,
    screenModel: MigrateDialogScreenModel,
    onDismissRequest: () -> Unit,
    onClickTitle: () -> Unit,
    onPopScreen: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state by screenModel.state.collectAsState()

    val flags = remember { MigrationFlags.getFlags(oldManga, screenModel.migrateFlags.get()) }
    val selectedFlags = remember { flags.map { it.isDefaultSelected }.toMutableStateList() }

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    flags.forEachIndexed { index, flag ->
                        LabeledCheckbox(
                            label = stringResource(flag.titleId),
                            checked = selectedFlags[index],
                            onCheckedChange = { selectedFlags[index] = it },
                        )
                    }
                }
            },
            confirmButton = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    TextButton(
                        onClick = { onClickTitle() },
                    ) {
                        Text(text = stringResource(MR.strings.action_show_manga))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = {
                            scope.launchIO {
                                screenModel.migrateManga(
                                    oldManga,
                                    newManga,
                                    false,
                                    MigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
                                )
                                withUIContext { onPopScreen() }
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.copy))
                    }
                    TextButton(
                        onClick = {
                            scope.launchIO {
                                screenModel.migrateManga(
                                    oldManga,
                                    newManga,
                                    true,
                                    MigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
                                )

                                withUIContext { onPopScreen() }
                            }
                        },
                    ) {
                        Text(text = stringResource(MR.strings.migrate))
                    }
                }
            },
        )
    }
}

internal class MigrateDialogScreenModel(
    private val sourceManager: SourceManager = Injekt.get(),
    // KMK -->
    sourcePreferences: SourcePreferences = Injekt.get(),
    // KMK <--
) : StateScreenModel<MigrateDialogScreenModel.State>(State()) {

    val migrateFlags: Preference<Int> by lazy {
        // KMK -->
        sourcePreferences.migrateFlags()
        // KMK <--
    }

    suspend fun migrateManga(
        oldManga: Manga,
        newManga: Manga,
        replace: Boolean,
        flags: Int,
    ) {
        migrateFlags.set(flags)
        val source = sourceManager.get(newManga.source) ?: return
        val prevSource = sourceManager.get(oldManga.source)

        mutableState.update { it.copy(isMigrating = true) }

        try {
            val chapters = source.getChapterList(newManga.toSManga())

            migrateMangaInternal(
                oldSource = prevSource,
                newSource = source,
                oldManga = oldManga,
                newManga = newManga,
                sourceChapters = chapters,
                replace = replace,
                presetFlags = flags,
            )
        } catch (e: Throwable) {
            // Explicitly stop if an error occurred; the dialog normally gets popped at the end anyway
            // KMK -->
            xLog(LogLevel.Error, "Failed to migrate manga ${oldManga.title} to ${newManga.title}", e)
        } finally {
            // KMK <--
            mutableState.update { it.copy(isMigrating = false) }
        }
    }

    companion object {
        suspend fun migrateMangaInternal(
            oldSource: Source?,
            newSource: Source,
            oldManga: Manga,
            newManga: Manga,
            sourceChapters: List<SChapter>,
            replace: Boolean,
            // KMK -->
            presetFlags: Int? = null,
            // KMK <--
        ) {
            // KMK -->
            if (oldManga.id == newManga.id) return // Nothing to migrate

            val sourcePreferences: SourcePreferences = Injekt.get()
            val downloadManager: DownloadManager = Injekt.get()
            val coverCache: CoverCache = Injekt.get()
            val updateManga: UpdateManga = Injekt.get()
            val syncChaptersWithSource: SyncChaptersWithSource = Injekt.get()
            val updateChapter: UpdateChapter = Injekt.get()
            val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get()
            val getHistory: GetHistory = Injekt.get()
            val upsertHistory: UpsertHistory = Injekt.get()
            val getCategories: GetCategories = Injekt.get()
            val setMangaCategories: SetMangaCategories = Injekt.get()
            val getTracks: GetTracks = Injekt.get()
            val insertTrack: InsertTrack = Injekt.get()
            val enhancedServices by lazy {
                Injekt.get<TrackerManager>().trackers.filterIsInstance<EnhancedTracker>()
            }

            val flags = presetFlags ?: sourcePreferences.migrateFlags().get()
            // KMK <--

            val migrateChapters = MigrationFlags.hasChapters(flags)
            val migrateCategories = MigrationFlags.hasCategories(flags)
            val migrateCustomCover = MigrationFlags.hasCustomCover(flags)
            val deleteDownloaded = MigrationFlags.hasDeleteDownloaded(flags)
            // KMK -->
            val migrateTracks = MigrationFlags.hasTracks(flags)
            val migrateExtra = MigrationFlags.hasExtra(flags)
            // KMK <--

            // Update newManga's chapters first to ensure it has latest chapter list
            try {
                syncChaptersWithSource.await(sourceChapters, newManga, newSource)
            } catch (_: Exception) {
                // Worst case, chapters won't be synced
            }

            // Update chapters read, bookmark and dateFetch
            if (migrateChapters) {
                val prevMangaChapters = getChaptersByMangaId.await(oldManga.id)
                val mangaChapters = getChaptersByMangaId.await(newManga.id)

                val maxChapterRead = prevMangaChapters
                    .filter { it.read }
                    .maxOfOrNull { it.chapterNumber }

                // SY -->
                val prevHistoryList = getHistory.await(oldManga.id)
                    // KMK -->
                    .associateBy { it.chapterId }
                // KMK <--

                val chapterUpdates = mutableListOf<ChapterUpdate>()
                val historyUpdates = mutableListOf<HistoryUpdate>()

                mangaChapters.forEach { updatedChapter ->
                    // SY <--
                    if (updatedChapter.isRecognizedNumber) {
                        val prevChapter = prevMangaChapters
                            .find { it.isRecognizedNumber && it.chapterNumber == updatedChapter.chapterNumber }

                        if (prevChapter != null) {
                            // SY -->
                            // If chapters match then mark new manga's chapters read/unread as old one
                            chapterUpdates += ChapterUpdate(
                                id = updatedChapter.id,
                                // Also migrate read status
                                read = prevChapter.read,
                                // SY <--
                                dateFetch = prevChapter.dateFetch,
                                bookmark = prevChapter.bookmark,
                            )
                            // SY -->
                            // KMK -->
                            prevHistoryList[prevChapter.id]?.let { prevHistory ->
                                // KMK <--
                                historyUpdates += HistoryUpdate(
                                    updatedChapter.id,
                                    prevHistory.readAt ?: return@let,
                                    prevHistory.readDuration,
                                )
                            }
                            // SY <--
                        } else if (maxChapterRead != null && updatedChapter.chapterNumber <= maxChapterRead) {
                            // SY -->
                            // If chapters which only present on new manga then mark read up to latest read chapter number
                            chapterUpdates += ChapterUpdate(
                                id = updatedChapter.id,
                                // SY <--
                                read = true,
                            )
                        }
                    }
                }

                updateChapter.awaitAll(chapterUpdates)
                // SY -->
                upsertHistory.awaitAll(historyUpdates)
                // SY <--
            }

            // Update categories
            if (migrateCategories) {
                val categoryIds = getCategories.await(oldManga.id).map { it.id }
                setMangaCategories.await(newManga.id, categoryIds)
            }

            // Update track
            // SY -->
            if (migrateTracks) {
                // SY <--
                getTracks.await(oldManga.id).mapNotNull { track ->
                    val updatedTrack = track.copy(mangaId = newManga.id)

                    // Kavita, Komga, Suwayomi
                    val service = enhancedServices
                        .firstOrNull { it.isTrackFrom(updatedTrack, oldManga, oldSource) }

                    if (service != null) {
                        service.migrateTrack(updatedTrack, newManga, newSource)
                    } else {
                        updatedTrack
                    }
                }
                    .takeIf { it.isNotEmpty() }
                    ?.let { insertTrack.awaitAll(it) }
            }

            // Delete downloaded
            if (deleteDownloaded) {
                if (oldSource != null) {
                    downloadManager.deleteManga(oldManga, oldSource)
                }
            }

            if (replace) {
                updateManga.awaitUpdateFavorite(oldManga.id, favorite = false)
            }

            // Update custom cover (recheck if custom cover exists)
            if (migrateCustomCover && oldManga.hasCustomCover()) {
                coverCache.setCustomCoverToCache(newManga, coverCache.getCustomCoverFile(oldManga.id).inputStream())
            }

            updateManga.await(
                MangaUpdate(
                    id = newManga.id,
                    favorite = true,
                    chapterFlags = oldManga.chapterFlags
                        // KMK -->
                        .takeIf { migrateExtra },
                    // KMK <--
                    viewerFlags = oldManga.viewerFlags
                        // KMK -->
                        .takeIf { migrateExtra },
                    // KMK <--
                    dateAdded = if (replace) oldManga.dateAdded else Instant.now().toEpochMilli(),
                    notes = oldManga.notes
                        // KMK -->
                        .takeIf { migrateExtra },
                    // KMK <--
                ),
            )
        }
    }

    @Immutable
    data class State(
        val isMigrating: Boolean = false,
    )
}
