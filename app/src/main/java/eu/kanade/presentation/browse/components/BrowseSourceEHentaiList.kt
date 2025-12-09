package eu.kanade.presentation.browse.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAny
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.gowtham.ratingbar.RatingBar
import com.gowtham.ratingbar.RatingBarStyle
import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.GRID_SELECTED_COVER_ALPHA
import eu.kanade.presentation.manga.components.MangaCover
import eu.kanade.presentation.manga.components.MangaCoverHide
import exh.debug.DebugToggles
import exh.metadata.MetadataUtil
import exh.metadata.metadata.EHentaiSearchMetadata
import exh.metadata.metadata.RaisedSearchMetadata
import exh.util.SourceTagsUtil
import exh.util.SourceTagsUtil.GenreColor
import exh.util.SourceTagsUtil.genreTextColor
import exh.util.floor
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.core.common.i18n.pluralStringResource
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.asMangaCover
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.BadgeGroup
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.icons.FlagEmoji.Companion.getEmojiLangFlag
import tachiyomi.presentation.core.util.selectedBackground
import java.time.Instant
import java.time.ZoneId

@Composable
fun BrowseSourceEHentaiList(
    mangaList: LazyPagingItems<StateFlow</* SY --> */Pair<Manga, RaisedSearchMetadata?>/* SY <-- */>>,
    contentPadding: PaddingValues,
    onMangaClick: (Manga) -> Unit,
    onMangaLongClick: (Manga) -> Unit,
    // KMK -->
    selection: List<Manga>,
    // KMK <--
) {
    LazyColumn(
        contentPadding = contentPadding,
    ) {
        item {
            if (mangaList.loadState.prepend is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }

        items(count = mangaList.itemCount) { index ->
            val pair by mangaList[index]?.collectAsState() ?: return@items
            val manga = pair.first
            val metadata = pair.second

            BrowseSourceEHentaiListItem(
                manga = manga,
                // SY -->
                metadata = metadata,
                // SY <--
                onClick = { onMangaClick(manga) },
                onLongClick = { onMangaLongClick(manga) },
                // KMK -->
                isSelected = selection.fastAny { selected -> selected.id == manga.id },
                // KMK <--
            )
        }

        item {
            if (mangaList.loadState.refresh is LoadState.Loading || mangaList.loadState.append is LoadState.Loading) {
                BrowseSourceLoadingItem()
            }
        }
    }
}

@Composable
fun BrowseSourceEHentaiListItem(
    manga: Manga,
    // SY -->
    metadata: RaisedSearchMetadata?,
    // SY <--
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = onClick,
    // KMK -->
    isSelected: Boolean = false,
    libraryColored: Boolean = true,
    // KMK <--
) {
    if (metadata !is EHentaiSearchMetadata) return
    // KMK -->
    val coverData = manga.asMangaCover()
    val bgColor = coverData.dominantCoverColors?.first?.let { Color(it) }.takeIf { libraryColored }
    val onBgColor = coverData.dominantCoverColors?.second.takeIf { libraryColored }
    val coverAlpha = if (manga.favorite) CommonMangaItemDefaults.BrowseFavoriteCoverAlpha else 1f
    // KMK <--

    val context = LocalContext.current
    val languageText by produceState("", metadata) {
        value = withIOContext {
            // KMK -->
            val locale = metadata.tags
                .filter { it.namespace == EHentaiSearchMetadata.EH_LANGUAGE_NAMESPACE }
                .firstNotNullOfOrNull {
                    SourceTagsUtil.getLocaleSourceUtil(it.name)
                }
            // KMK <--
            val pageCount = metadata.length
            if (locale != null && pageCount != null) {
                context.pluralStringResource(
                    SYMR.plurals.browse_language_and_pages,
                    pageCount,
                    pageCount,
                    // KMK -->
                    getEmojiLangFlag(
                        // KMK <--
                        locale.toLanguageTag(), // KMK: .uppercase()
                    ),
                )
            } else if (pageCount != null) {
                context.pluralStringResource(SYMR.plurals.num_pages, pageCount, pageCount)
            } else {
                locale?.toLanguageTag()
                    // KMK -->
                    ?.let { getEmojiLangFlag(it) }
                    // .uppercase()
                    // KMK <--
                    .orEmpty()
            }
        }
    }
    val datePosted by produceState("", metadata) {
        value = withIOContext {
            runCatching {
                metadata.datePosted?.let {
                    MetadataUtil.EX_DATE_FORMAT.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
                }
            }.getOrNull().orEmpty()
        }
    }
    val genre by produceState<Pair<GenreColor, StringResource>?>(null, metadata) {
        value = withIOContext {
            when (metadata.genre) {
                "doujinshi" -> GenreColor.DOUJINSHI_COLOR to SYMR.strings.doujinshi
                "manga" -> GenreColor.MANGA_COLOR to SYMR.strings.entry_type_manga
                "artistcg" -> GenreColor.ARTIST_CG_COLOR to SYMR.strings.artist_cg
                "gamecg" -> GenreColor.GAME_CG_COLOR to SYMR.strings.game_cg
                "western" -> GenreColor.WESTERN_COLOR to SYMR.strings.western
                "non-h" -> GenreColor.NON_H_COLOR to SYMR.strings.non_h
                "imageset" -> GenreColor.IMAGE_SET_COLOR to SYMR.strings.image_set
                "cosplay" -> GenreColor.COSPLAY_COLOR to SYMR.strings.cosplay
                "asianporn" -> GenreColor.ASIAN_PORN_COLOR to SYMR.strings.asian_porn
                "misc" -> GenreColor.MISC_COLOR to SYMR.strings.misc
                else -> null
            }
        }
    }
    val rating by produceState(0f, metadata) {
        value = withIOContext {
            val rating = metadata.averageRating?.toFloat()
            rating?.div(0.5F)?.floor()?.let { 0.5F.times(it) } ?: 0f
        }
    }

    Row(
        modifier = Modifier
            .selectedBackground(isSelected)
            .height(148.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            // KMK -->
            if (DebugToggles.HIDE_COVER_IMAGE_ONLY_SHOW_COLOR.enabled) {
                MangaCoverHide.Book(
                    modifier = Modifier
                        .fillMaxHeight(),
                    bgColor = bgColor ?: (MaterialTheme.colorScheme.surface.takeIf { isSelected }),
                    tint = onBgColor,
                )
            } else {
                // KMK <--
                MangaCover.Book(
                    modifier = Modifier
                        .fillMaxHeight(),
                    // KMK -->
                    alpha = if (isSelected) GRID_SELECTED_COVER_ALPHA else coverAlpha,
                    bgColor = bgColor ?: (MaterialTheme.colorScheme.surface.takeIf { isSelected }),
                    tint = onBgColor,
                    // KMK <--
                    data = coverData,
                )
            }
            if (manga.favorite) {
                BadgeGroup(
                    modifier = Modifier
                        .padding(4.dp)
                        .align(Alignment.TopStart),
                ) {
                    // KMK -->
                    InLibraryBadge(enabled = true)
                    // KMK <--
                }
            }
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = manga.title,
                    maxLines = 2,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    overflow = TextOverflow.Ellipsis,
                )
                metadata.uploader?.let {
                    Text(
                        text = it,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 14.sp,
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, start = 8.dp, end = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    horizontalAlignment = Alignment.Start,
                ) {
                    RatingBar(
                        value = rating,
                        onValueChange = {},
                        onRatingChanged = {},
                        isIndicator = true,
                        numOfStars = 5,
                        size = 18.dp,
                        style = RatingBarStyle.Fill(),
                    )
                    val color = genre?.first?.color
                    // KMK -->
                    val textColor = genre?.first?.let(::genreTextColor)?.let(::Color) ?: Color.Unspecified
                    // KMK <--
                    val res = genre?.second
                    Card(
                        colors = if (color != null) {
                            CardDefaults.cardColors(Color(color))
                        } else {
                            CardDefaults.cardColors()
                        },
                    ) {
                        Text(
                            text = if (res != null) {
                                stringResource(res)
                            } else {
                                metadata.genre.orEmpty()
                            },
                            // KMK -->
                            color = textColor,
                            // KMK <--
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp),
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        languageText,
                        maxLines = 1,
                        fontSize = 14.sp,
                    )
                    Text(
                        datePosted,
                        maxLines = 1,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
