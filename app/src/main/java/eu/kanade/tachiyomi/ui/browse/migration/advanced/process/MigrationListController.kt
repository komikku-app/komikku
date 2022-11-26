package eu.kanade.tachiyomi.ui.browse.migration.advanced.process

import android.os.Bundle
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.bundleOf
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.domain.manga.model.Manga
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.Badge
import eu.kanade.presentation.components.BadgeGroup
import eu.kanade.presentation.components.MangaCover
import eu.kanade.presentation.components.Scaffold
import eu.kanade.presentation.components.ScrollbarLazyColumn
import eu.kanade.presentation.util.plus
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.presentation.util.topSmallPaddingValues
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.base.changehandler.OneWayFadeChangeHandler
import eu.kanade.tachiyomi.ui.base.controller.FullComposeController
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.migration.MigrationMangaDialog
import eu.kanade.tachiyomi.ui.browse.migration.advanced.design.PreMigrationController
import eu.kanade.tachiyomi.ui.browse.migration.advanced.process.MigratingManga.SearchResult
import eu.kanade.tachiyomi.ui.browse.migration.search.SearchController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.lang.withIOContext
import eu.kanade.tachiyomi.util.system.getParcelableCompat
import eu.kanade.tachiyomi.util.system.toast
import me.saket.cascade.CascadeDropdownMenu

class MigrationListController(bundle: Bundle? = null) :
    FullComposeController<MigrationListPresenter>(bundle) {

    constructor(config: MigrationProcedureConfig) : this(
        bundleOf(
            CONFIG_EXTRA to config,
        ),
    )

    val config = args.getParcelableCompat<MigrationProcedureConfig>(CONFIG_EXTRA)

    private var selectedMangaId: Long? = null
    private var manualMigrations = 0

    override fun createPresenter(): MigrationListPresenter {
        return MigrationListPresenter(config!!)
    }

    @Composable
    override fun ComposeContent() {
        val items by presenter.migratingItems.collectAsState()
        val migrationDone by presenter.migrationDone.collectAsState()
        val unfinishedCount by presenter.unfinishedCount.collectAsState()
        Scaffold(
            topBar = { scrollBehavior ->
                val titleString = stringResource(R.string.migration)
                val title by produceState(initialValue = titleString, items, unfinishedCount, titleString) {
                    withIOContext {
                        value = "$titleString ($unfinishedCount/${items.size})"
                    }
                }
                AppBar(
                    title = title,
                    actions = {
                        IconButton(
                            onClick = { openMigrationDialog(true) },
                            enabled = migrationDone,
                        ) {
                            Icon(
                                imageVector = if (items.size == 1) Icons.Outlined.ContentCopy else Icons.Outlined.CopyAll,
                                contentDescription = stringResource(R.string.copy),
                            )
                        }
                        IconButton(
                            onClick = { openMigrationDialog(false) },
                            enabled = migrationDone,
                        ) {
                            Icon(
                                imageVector = if (items.size == 1) Icons.Outlined.Done else Icons.Outlined.DoneAll,
                                contentDescription = stringResource(R.string.migrate),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { contentPadding ->
            ScrollbarLazyColumn(
                contentPadding = contentPadding + topSmallPaddingValues,
            ) {
                items(items, key = { it.manga.id }) { migrationItem ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .animateItemPlacement()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val result by migrationItem.searchResult.collectAsState()
                        MigrationItem(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .weight(1f),
                            manga = migrationItem.manga,
                            sourcesString = migrationItem.sourcesString,
                            chapterInfo = migrationItem.chapterInfo,
                            onClick = {
                                router.pushController(
                                    MangaController(
                                        migrationItem.manga.id,
                                        true,
                                    ),
                                )
                            },
                        )

                        Icon(
                            Icons.Outlined.ArrowForward,
                            contentDescription = stringResource(R.string.migrating_to),
                            modifier = Modifier.weight(0.2f),
                        )

                        MigrationItemResult(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .weight(1f),
                            migrationItem = migrationItem,
                            result = result,
                        )

                        MigrationActionIcon(
                            modifier = Modifier
                                .weight(0.2f),
                            result = result,
                            skipManga = { presenter.removeManga(migrationItem.manga.id) },
                            searchManually = {
                                val manga = migrationItem.manga
                                selectedMangaId = manga.id
                                val sources = presenter.getMigrationSources()
                                val validSources = if (sources.size == 1) {
                                    sources
                                } else {
                                    sources.filter { it.id != manga.source }
                                }
                                val searchController = SearchController(manga, validSources)
                                searchController.targetController = this@MigrationListController
                                router.pushController(searchController)
                            },
                            migrateNow = {
                                migrateManga(migrationItem.manga.id, false)
                                manualMigrations++
                            },
                            copyNow = {
                                migrateManga(migrationItem.manga.id, true)
                                manualMigrations++
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun MigrationItem(
        modifier: Modifier,
        manga: Manga,
        sourcesString: String,
        chapterInfo: MigratingManga.ChapterInfo,
        onClick: () -> Unit,
    ) {
        Column(
            modifier
                .widthIn(max = 150.dp)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
                .padding(4.dp),
        ) {
            val context = LocalContext.current
            Box(
                Modifier.fillMaxWidth()
                    .aspectRatio(MangaCover.Book.ratio),
            ) {
                MangaCover.Book(
                    modifier = Modifier
                        .fillMaxWidth(),
                    data = manga,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color(0xAA000000),
                            ),
                        )
                        .fillMaxHeight(0.33f)
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                )
                Text(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.BottomStart),
                    text = manga.title.ifBlank { stringResource(R.string.unknown) },
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = Color.White,
                        shadow = Shadow(
                            color = Color.Black,
                            blurRadius = 4f,
                        ),
                    ),
                )
                BadgeGroup(modifier = Modifier.padding(4.dp)) {
                    Badge(text = "${chapterInfo.chapterCount}")
                }
            }
            Text(
                text = sourcesString,
                modifier = Modifier.padding(top = 4.dp, bottom = 1.dp, start = 8.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.titleSmall,
            )

            val formattedLatestChapter by produceState(initialValue = "") {
                value = withIOContext {
                    chapterInfo.getFormattedLatestChapter(context)
                }
            }
            Text(
                text = formattedLatestChapter,
                modifier = Modifier.padding(top = 1.dp, bottom = 4.dp, start = 8.dp),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    @Composable
    fun MigrationActionIcon(
        modifier: Modifier,
        result: SearchResult,
        skipManga: () -> Unit,
        searchManually: () -> Unit,
        migrateNow: () -> Unit,
        copyNow: () -> Unit,
    ) {
        var moreExpanded by remember { mutableStateOf(false) }
        val closeMenu = { moreExpanded = false }

        Box(modifier) {
            if (result is SearchResult.Searching) {
                IconButton(onClick = skipManga) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.action_stop),
                    )
                }
            } else if (result is SearchResult.Result || result is SearchResult.NotFound) {
                IconButton(onClick = { moreExpanded = !moreExpanded }) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.abc_action_menu_overflow_description),
                    )
                }
                CascadeDropdownMenu(
                    expanded = moreExpanded,
                    onDismissRequest = closeMenu,
                    offset = DpOffset(8.dp, (-56).dp),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_search_manually)) },
                        onClick = {
                            searchManually()
                            closeMenu()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_skip_manga)) },
                        onClick = {
                            skipManga()
                            closeMenu()
                        },
                    )
                    if (result is SearchResult.Result) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_migrate_now)) },
                            onClick = {
                                migrateNow()
                                closeMenu()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_copy_now)) },
                            onClick = {
                                copyNow()
                                closeMenu()
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun MigrationItemResult(modifier: Modifier, migrationItem: MigratingManga, result: SearchResult) {
        Box(modifier) {
            when (result) {
                SearchResult.Searching -> Box(
                    modifier = Modifier
                        .widthIn(max = 150.dp)
                        .fillMaxWidth()
                        .aspectRatio(MangaCover.Book.ratio),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                SearchResult.NotFound -> Image(
                    painter = rememberResourceBitmapPainter(id = R.drawable.cover_error),
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(4.dp)),
                    contentScale = ContentScale.Crop,
                )
                is SearchResult.Result -> {
                    val item by produceState<Triple<Manga, MigratingManga.ChapterInfo, String>?>(
                        initialValue = null,
                        migrationItem,
                        result,
                    ) {
                        value = withIOContext {
                            val manga = presenter.getManga(result) ?: return@withIOContext null
                            Triple(
                                manga,
                                presenter.getChapterInfo(result),
                                presenter.getSourceName(manga),
                            )
                        }
                    }
                    if (item != null) {
                        val (manga, chapterInfo, source) = item!!
                        MigrationItem(
                            modifier = Modifier,
                            manga = manga,
                            sourcesString = source,
                            chapterInfo = chapterInfo,
                            onClick = {
                                router.pushController(
                                    MangaController(
                                        manga.id,
                                        true,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    private fun noMigration() {
        val res = resources
        if (res != null) {
            activity?.toast(
                res.getQuantityString(
                    R.plurals.manga_migrated,
                    manualMigrations,
                    manualMigrations,
                ),
            )
        }
        if (!presenter.hideNotFound) {
            router.popCurrentController()
        }
    }

    fun useMangaForMigration(manga: Manga, source: Source) {
        presenter.useMangaForMigration(manga, source, selectedMangaId ?: return)
    }

    fun migrateMangas() {
        presenter.migrateMangas()
    }

    fun copyMangas() {
        presenter.copyMangas()
    }

    fun migrateManga(mangaId: Long, copy: Boolean) {
        presenter.migrateManga(mangaId, copy)
    }

    fun removeManga(mangaId: Long) {
        presenter.removeManga(mangaId)
    }

    fun sourceFinished() {
        if (presenter.migratingItems.value.isEmpty()) noMigration()
    }

    fun navigateOut(manga: Manga?) {
        if (manga != null) {
            val newStack = router.backstack.filter {
                it.controller !is MangaController &&
                    it.controller !is MigrationListController &&
                    it.controller !is PreMigrationController
            } + MangaController(manga.id).withFadeTransaction()
            router.setBackstack(newStack, OneWayFadeChangeHandler())
            return
        }
        router.popCurrentController()
    }

    override fun handleBack(): Boolean {
        activity?.let {
            MaterialAlertDialogBuilder(it)
                .setTitle(R.string.stop_migrating)
                .setPositiveButton(R.string.action_stop) { _, _ ->
                    router.popCurrentController()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        return true
    }

    private fun openMigrationDialog(copy: Boolean) {
        val totalManga = presenter.migratingItems.value.size
        val mangaSkipped = presenter.mangasSkipped()
        MigrationMangaDialog(
            this,
            copy,
            totalManga,
            mangaSkipped,
        ).showDialog(router)
    }

    companion object {
        const val CONFIG_EXTRA = "config_extra"
        const val TAG = "migration_list"
    }
}
