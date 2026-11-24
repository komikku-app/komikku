package app.komikku.desktop.ui.tabs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.komikku.desktop.extensions.DesktopExtensionManager
import app.komikku.desktop.extensions.ExtensionIconDrawable
import app.komikku.desktop.ui.CoverImage
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.ui.library.LibraryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.domain.source.model.Source
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.Badge
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * The library page in the user's display mode (`LibraryPreferences.displayMode()`, Android's modes): compact
 * grid (title over the cover), comfortable grid (title below; also used for the KMK panorama variant),
 * cover-only grid, list. Grid columns from the column preference (0 = automatic, like Android's 128 dp
 * adaptive cells). Badges as on Android (`LibraryItem` counts are 0 / empty when a badge preference is off).
 */
@Composable
internal fun LibraryItems(
    items: List<LibraryItem>,
    displayMode: LibraryDisplayMode,
    columns: Int,
    selection: Set<Long>,
    onClick: (Long, LibraryClickModifiers) -> Unit,
    onLongClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onContinueReading: ((LibraryItem) -> Unit)? = null,
) {
    // P12: Android's "continue reading" button, on entries with unread chapters while the preference is on
    fun continueReading(item: LibraryItem): (() -> Unit)? =
        onContinueReading?.takeIf { item.libraryManga.unreadCount > 0 }?.let { { it(item) } }
    if (displayMode == LibraryDisplayMode.List) {
        LazyColumn(modifier = modifier.fillMaxSize().testTag("library-list"), contentPadding = PaddingValues(8.dp)) {
            items(items = items, key = { it.id }) { item ->
                LibraryListItem(item, Modifier.libraryItemClicks(item.id, item.id in selection, onClick, onLongClick), continueReading(item))
            }
        }
        return
    }
    LazyVerticalGrid(
        columns = if (columns > 0) GridCells.Fixed(columns) else GridCells.Adaptive(128.dp),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxSize().testTag("library-grid"),
    ) {
        items(items = items, key = { it.id }) { item ->
            LibraryGridItem(item, displayMode, Modifier.libraryItemClicks(item.id, item.id in selection, onClick, onLongClick), continueReading(item))
        }
    }
}

/**
 * Click handling of a library entry: click, long press (Android's range selection) and the keyboard modifiers held at
 * the press (Shift: range, Ctrl / Cmd: toggle); selected entries are tinted like Android's selected items.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.libraryItemClicks(
    id: Long,
    selected: Boolean,
    onClick: (Long, LibraryClickModifiers) -> Unit,
    onLongClick: (Long) -> Unit,
): Modifier {
    val pressModifiers = remember { arrayOf(LibraryClickModifiers()) }
    return this
        .clip(RoundedCornerShape(6.dp))
        .then(
            if (selected) Modifier.background(MaterialTheme.colorScheme.secondary.copy(alpha = SELECTED_ALPHA)) else Modifier,
        )
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) {
                        val keys = event.keyboardModifiers
                        pressModifiers[0] = LibraryClickModifiers(
                            shift = keys.isShiftPressed,
                            toggle = keys.isCtrlPressed || keys.isMetaPressed,
                        )
                    }
                }
            }
        }
        .combinedClickable(
            onClick = { onClick(id, pressModifiers[0]).also { pressModifiers[0] = LibraryClickModifiers() } },
            // A long press ends the press too: forget its modifiers, or the next (touch) click would reuse them.
            onLongClick = { onLongClick(id).also { pressModifiers[0] = LibraryClickModifiers() } },
        )
        .semantics { this.selected = selected }
}

@Composable
private fun LibraryGridItem(item: LibraryItem, displayMode: LibraryDisplayMode, clicks: Modifier, onContinueReading: (() -> Unit)?) {
    val manga = item.libraryManga.manga
    val titleBelow = displayMode == LibraryDisplayMode.ComfortableGrid ||
        displayMode == LibraryDisplayMode.ComfortableGridPanorama
    Column(
        modifier = clicks
            .padding(4.dp)
            .testTag("library-item-${item.id}"),
    ) {
        Box {
            LibraryCover(item, Modifier.fillMaxWidth().aspectRatio(MANGA_COVER_RATIO))
            LibraryBadges(item, Modifier.padding(4.dp))
            if (displayMode == LibraryDisplayMode.CompactGrid) {
                Text(
                    text = manga.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000))))
                        .padding(6.dp)
                        // Leave room for the continue reading button (Android's compact grid does the same)
                        .padding(end = if (onContinueReading != null) 28.dp else 0.dp),
                )
            }
            if (onContinueReading != null) {
                ContinueReadingButton(item, onContinueReading, Modifier.align(Alignment.BottomEnd).padding(6.dp))
            }
        }
        if (titleBelow) {
            Text(
                text = manga.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun LibraryListItem(item: LibraryItem, clicks: Modifier, onContinueReading: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(clicks)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("library-item-${item.id}"),
    ) {
        LibraryCover(item, Modifier.height(56.dp).aspectRatio(MANGA_COVER_RATIO))
        Text(
            text = item.libraryManga.manga.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        LibraryBadges(item, Modifier)
        if (onContinueReading != null) ContinueReadingButton(item, onContinueReading, Modifier.padding(start = 8.dp))
    }
}

/** Android's `ContinueReadingButton` (28 dp, play icon, "Resume"): opens the entry's next unread chapter. */
@Composable
private fun ContinueReadingButton(item: LibraryItem, onClick: () -> Unit, modifier: Modifier) {
    FilledIconButton(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            contentColor = contentColorFor(MaterialTheme.colorScheme.primaryContainer),
        ),
        modifier = modifier.size(28.dp).testTag("continue-reading-${item.id}"),
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(MR.strings.action_resume), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun LibraryCover(item: LibraryItem, modifier: Modifier) {
    val manga = item.libraryManga.manga
    val cover = remember(manga) { manga.asMangaCover() }
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        CoverImage(
            model = cover,
            contentDescription = manga.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)),
        )
    }
}

/**
 * Android's library badges: downloads + unread (start), local folder or source language, and Komikku's source icon
 * (end; P12, `sourceBadge`).
 */
@Composable
private fun LibraryBadges(item: LibraryItem, modifier: Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (item.downloadCount > 0 || item.unreadCount > 0) {
            BadgeGroup {
                if (item.downloadCount > 0) {
                    Badge(
                        text = "${item.downloadCount}",
                        color = MaterialTheme.colorScheme.tertiary,
                        textColor = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.testTag("download-badge-${item.id}"),
                    )
                }
                if (item.unreadCount > 0) {
                    Badge(text = "${item.unreadCount}", modifier = Modifier.testTag("unread-badge-${item.id}"))
                }
            }
        }
        if (item.isLocal || item.sourceLanguage.isNotEmpty() || item.source != null) {
            BadgeGroup {
                if (item.isLocal) {
                    Badge(
                        imageVector = Icons.Outlined.Folder,
                        color = MaterialTheme.colorScheme.tertiary,
                        iconColor = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.testTag("local-badge-${item.id}"),
                    )
                } else if (item.sourceLanguage.isNotEmpty()) {
                    // Desktop has no flag icons: the language code as text (Android's `useLangIcon = false` look).
                    Badge(
                        text = item.sourceLanguage.uppercase(),
                        color = MaterialTheme.colorScheme.tertiary,
                        textColor = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.testTag("language-badge-${item.id}"),
                    )
                }
                item.source?.let { SourceIconBadge(it, Modifier.testTag("source-badge-${item.id}")) }
            }
        }
    }
}

/**
 * KMK: Android's `SourceIconBadge` (P12): the extension's icon (from its APK), a warning for a missing source, a folder
 * for the local source, else the default source icon. The installed extensions are collected, so that an icon of an
 * extension loaded after the library was shown appears (P12 review).
 */
@Composable
internal fun SourceIconBadge(
    source: Source,
    modifier: Modifier,
    installedExtensions: StateFlow<List<Extension.Installed>> = remember { SourceIcons.installedExtensions() },
) {
    val installed by installedExtensions.collectAsState()
    val isStub = source.isStub
    val icon = remember(source.id, installed, isStub) { SourceIcons.of(source.id, installed) }
    when {
        isStub && icon == null -> Badge(
            imageVector = Icons.Filled.Warning,
            iconColor = MaterialTheme.colorScheme.error,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = modifier.semantics { stateDescription = SourceIcons.MISSING },
        )
        icon != null -> Badge(
            imageBitmap = icon,
            modifier = modifier.semantics { stateDescription = SourceIcons.ICON }.scale(1.3f).height(18.dp),
        )
        source.isLocal() -> Badge(
            imageVector = Icons.Outlined.Folder,
            color = MaterialTheme.colorScheme.tertiary,
            iconColor = MaterialTheme.colorScheme.onTertiary,
            modifier = modifier.semantics { stateDescription = SourceIcons.LOCAL },
        )
        else -> Badge(
            imageVector = Icons.Outlined.LocalLibrary,
            color = MaterialTheme.colorScheme.tertiary,
            iconColor = MaterialTheme.colorScheme.onTertiary,
            modifier = modifier.semantics { stateDescription = SourceIcons.DEFAULT },
        )
    }
}

/**
 * Decoded extension icons by source id (`null`: no installed extension icon, or it does not decode), forgotten when the
 * installed extensions change.
 */
internal object SourceIcons {
    /** The badge kinds, as the badge's state description (tests). */
    const val MISSING = "missing"
    const val ICON = "icon"
    const val LOCAL = "local"
    const val DEFAULT = "default"

    private val cache = ConcurrentHashMap<Long, Optional<ImageBitmap>>()

    @Volatile
    private var extensions: List<Extension.Installed>? = null

    /** The extension manager's installed extensions; none when there is no manager (some tests). */
    fun installedExtensions(): StateFlow<List<Extension.Installed>> = try {
        Injekt.get<DesktopExtensionManager>().installedExtensionsFlow
    } catch (_: Exception) {
        MutableStateFlow(emptyList())
    }

    fun of(sourceId: Long, installed: List<Extension.Installed>): ImageBitmap? {
        synchronized(this) {
            if (installed !== extensions) {
                cache.clear()
                extensions = installed
            }
        }
        return iconOf(sourceId, installed)
    }

    private fun iconOf(sourceId: Long, installed: List<Extension.Installed>): ImageBitmap? = cache.getOrPut(sourceId) {
        val bytes = installed
            .firstOrNull { extension -> extension.sources.any { it.id == sourceId } }
            ?.let { (it.icon as? ExtensionIconDrawable)?.bytes }
        Optional.ofNullable(
            bytes?.let {
                try {
                    org.jetbrains.skia.Image.makeFromEncoded(it).toComposeImageBitmap()
                } catch (_: Exception) {
                    null
                }
            },
        )
    }.orElse(null)
}

private const val MANGA_COVER_RATIO = 2f / 3f

/** Tint of selected entries (Android's `GridSelectedCoverAlpha`-like highlight). */
private const val SELECTED_ALPHA = 0.35f
