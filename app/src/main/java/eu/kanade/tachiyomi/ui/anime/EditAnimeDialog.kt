package eu.kanade.tachiyomi.ui.anime

import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.children
import coil3.load
import coil3.request.transformations
import coil3.transform.RoundedCornersTransformation
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.presentation.anime.components.RatioSwitchToPanorama
import eu.kanade.presentation.track.components.TrackLogoIcon
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.track.EnhancedTracker
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.databinding.EditMangaDialogBinding
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.util.lang.chop
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.widget.materialdialogs.binding
import eu.kanade.tachiyomi.widget.materialdialogs.dismissDialog
import eu.kanade.tachiyomi.widget.materialdialogs.setColors
import eu.kanade.tachiyomi.widget.materialdialogs.setHint
import eu.kanade.tachiyomi.widget.materialdialogs.setNegativeButton
import eu.kanade.tachiyomi.widget.materialdialogs.setPositiveButton
import eu.kanade.tachiyomi.widget.materialdialogs.setTextEdit
import eu.kanade.tachiyomi.widget.materialdialogs.setTitle
import exh.util.dropBlank
import exh.util.trimOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.source.local.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

@Composable
fun EditAnimeDialog(
    anime: Anime,
    // KMK -->
    coverRatio: MutableFloatState,
    // KMK <--
    onDismissRequest: () -> Unit,
    onPositiveClick: (
        title: String?,
        author: String?,
        artist: String?,
        thumbnailUrl: String?,
        description: String?,
        tags: List<String>?,
        status: Long?,
    ) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var binding by remember {
        mutableStateOf<EditMangaDialogBinding?>(null)
    }
    val showTrackerSelectionDialogue = remember { mutableStateOf(false) }
    val getTracks = remember { Injekt.get<GetTracks>() }
    val trackerManager = remember { Injekt.get<TrackerManager>() }
    val tracks = remember { mutableStateOf(emptyList<Pair<Track, Tracker>>()) }
    // KMK -->
    val colors = EditAnimeDialogColors(
        textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
        textHighlightColor = MaterialTheme.colorScheme.inversePrimary.toArgb(),
        iconColor = MaterialTheme.colorScheme.primary.toArgb(),
        tagColor = MaterialTheme.colorScheme.outlineVariant.toArgb(),
        tagTextColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb(),
        btnTextColor = MaterialTheme.colorScheme.onPrimary.toArgb(),
        btnBgColor = MaterialTheme.colorScheme.surfaceTint.toArgb(),
        dropdownBgColor = MaterialTheme.colorScheme.surfaceVariant.toArgb(),
        dialogBgColor = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb(),
    )
    // KMK <--
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    @Suppress("NAME_SHADOWING")
                    val binding = binding ?: return@TextButton
                    onPositiveClick(
                        binding.title.text.toString(),
                        binding.mangaAuthor.text.toString(),
                        binding.mangaArtist.text.toString(),
                        binding.thumbnailUrl.text.toString(),
                        binding.mangaDescription.text.toString(),
                        binding.mangaGenresTags.getTextStrings(),
                        binding.status.selectedItemPosition.let {
                            when (it) {
                                1 -> SAnime.ONGOING
                                2 -> SAnime.COMPLETED
                                3 -> SAnime.LICENSED
                                4 -> SAnime.PUBLISHING_FINISHED
                                5 -> SAnime.CANCELLED
                                6 -> SAnime.ON_HIATUS
                                else -> null
                            }
                        }?.toLong(),
                    )
                    onDismissRequest()
                },
            ) {
                Text(stringResource(MR.strings.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                AndroidView(
                    factory = { factoryContext ->
                        EditMangaDialogBinding.inflate(LayoutInflater.from(factoryContext))
                            .also { binding = it }
                            .apply {
                                onViewCreated(
                                    anime,
                                    factoryContext,
                                    this,
                                    scope,
                                    getTracks,
                                    trackerManager,
                                    tracks,
                                    showTrackerSelectionDialogue,
                                    // KMK -->
                                    colors,
                                    coverRatio = coverRatio,
                                    // KMK <--
                                )
                            }
                            .root
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )

    if (showTrackerSelectionDialogue.value) {
        TrackerSelectDialog(
            tracks = tracks.value,
            onDismissRequest = { showTrackerSelectionDialogue.value = false },
            onTrackerSelect = { tracker, track ->
                scope.launch {
                    autofillFromTracker(binding!!, track, tracker)
                }
            },
        )
    }
}

@Composable
private fun TrackerSelectDialog(
    tracks: List<Pair<Track, Tracker>>,
    onDismissRequest: () -> Unit,
    onTrackerSelect: (
        tracker: Tracker,
        track: Track,
    ) -> Unit,
) {
    AlertDialog(
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_cancel))
            }
        },
        title = {
            Text(stringResource(SYMR.strings.select_tracker))
        },
        text = {
            FlowRow(
                modifier = Modifier
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                tracks.forEach { (track, tracker) ->
                    TrackLogoIcon(
                        tracker,
                        onClick = {
                            onTrackerSelect(tracker, track)
                            onDismissRequest()
                        },
                    )
                }
            }
        },
    )
}

// KMK -->
data class EditAnimeDialogColors(
    @ColorInt val textColor: Int,
    @ColorInt val textHighlightColor: Int,
    @ColorInt val iconColor: Int,
    @ColorInt val tagColor: Int,
    @ColorInt val tagTextColor: Int,
    @ColorInt val btnTextColor: Int,
    @ColorInt val btnBgColor: Int,
    @ColorInt val dropdownBgColor: Int,
    @ColorInt val dialogBgColor: Int,
)
// KMK <--

private fun onViewCreated(
    anime: Anime,
    context: Context,
    binding: EditMangaDialogBinding,
    scope: CoroutineScope,
    getTracks: GetTracks,
    trackerManager: TrackerManager,
    tracks: MutableState<List<Pair<Track, Tracker>>>,
    showTrackerSelectionDialogue: MutableState<Boolean>,
    // KMK -->
    colors: EditAnimeDialogColors,
    coverRatio: MutableFloatState,
    // KMK <--
) {
    loadCover(
        anime,
        binding,
        // KMK -->
        coverRatio,
        // KMK <--
    )

    // KMK -->
    // val statusAdapter: ArrayAdapter<String> = ArrayAdapter(
    val statusAdapter = SpinnerAdapter(
        // KMK <--
        context,
        android.R.layout.simple_spinner_dropdown_item,
        listOf(
            MR.strings.label_default,
            MR.strings.ongoing,
            MR.strings.completed,
            MR.strings.licensed,
            MR.strings.publishing_finished,
            MR.strings.cancelled,
            MR.strings.on_hiatus,
        ).map { context.stringResource(it) },
        // KMK -->
        colors,
        // KMK <--
    )

    binding.status.adapter = statusAdapter
    if (anime.status != anime.ogStatus) {
        binding.status.setSelection(
            when (anime.status.toInt()) {
                SAnime.UNKNOWN -> 0
                SAnime.ONGOING -> 1
                SAnime.COMPLETED -> 2
                SAnime.LICENSED -> 3
                SAnime.PUBLISHING_FINISHED, 61 -> 4
                SAnime.CANCELLED, 62 -> 5
                SAnime.ON_HIATUS, 63 -> 6
                else -> 0
            },
        )
    }

    // KMK -->
    // Set Spinner's selected item's background color to transparent
    binding.status.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
            if (view != null) (view as TextView).setBackgroundColor(0x00000000)
        }
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }

    // Set Spinner's dropdown caret color
    binding.status.backgroundTintList = ColorStateList.valueOf(colors.iconColor)
    // KMK

    if (anime.isLocal()) {
        if (anime.title != anime.url) {
            binding.title.setText(anime.title)
        }

        binding.title.hint = context.stringResource(SYMR.strings.title_hint, anime.url)
        binding.mangaAuthor.setText(anime.author.orEmpty())
        binding.mangaArtist.setText(anime.artist.orEmpty())
        binding.thumbnailUrl.setText(anime.thumbnailUrl.orEmpty())
        binding.mangaDescription.setText(anime.description.orEmpty())
        binding.mangaGenresTags.setChips(anime.genre.orEmpty().dropBlank(), scope, colors)
    } else {
        if (anime.title != anime.ogTitle) {
            binding.title.append(anime.title)
        }
        if (anime.author != anime.ogAuthor) {
            binding.mangaAuthor.append(anime.author.orEmpty())
        }
        if (anime.artist != anime.ogArtist) {
            binding.mangaArtist.append(anime.artist.orEmpty())
        }
        if (anime.thumbnailUrl != anime.ogThumbnailUrl) {
            binding.thumbnailUrl.append(anime.thumbnailUrl.orEmpty())
        }
        if (anime.description != anime.ogDescription) {
            binding.mangaDescription.append(anime.description.orEmpty())
        }
        binding.mangaGenresTags.setChips(anime.genre.orEmpty().dropBlank(), scope, colors)

        binding.title.hint = context.stringResource(SYMR.strings.title_hint, anime.ogTitle)

        binding.mangaAuthor.hint = context.stringResource(SYMR.strings.author_hint, anime.ogAuthor ?: "")
        binding.mangaArtist.hint = context.stringResource(SYMR.strings.artist_hint, anime.ogArtist ?: "")
        binding.mangaDescription.hint =
            context.stringResource(
                SYMR.strings.description_hint,
                anime.ogDescription?.takeIf { it.isNotBlank() }?.replace("\n", " ")?.chop(20) ?: "",
            )
        binding.thumbnailUrl.hint =
            context.stringResource(
                SYMR.strings.thumbnail_url_hint,
                anime.ogThumbnailUrl?.let {
                    it.chop(40) + if (it.length > 46) "." + it.substringAfterLast(".").chop(6) else ""
                } ?: "",
            )
    }
    binding.mangaGenresTags.clearFocus()

    // KMK -->
    listOf(
        binding.title,
        binding.mangaAuthor,
        binding.mangaArtist,
        binding.thumbnailUrl,
        binding.mangaDescription,
    ).forEach {
        it.setTextColor(colors.textColor)
        it.highlightColor = colors.textHighlightColor

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            it.textSelectHandle?.let { drawable ->
                drawable.setTint(colors.iconColor)
                it.setTextSelectHandle(drawable)
            }
            it.textSelectHandleLeft?.let { drawable ->
                drawable.setTint(colors.iconColor)
                it.setTextSelectHandleLeft(drawable)
            }
            it.textSelectHandleRight?.let { drawable ->
                drawable.setTint(colors.iconColor)
                it.setTextSelectHandleRight(drawable)
            }
        }
    }
    listOf(
        binding.titleOutline,
        binding.mangaAuthorOutline,
        binding.mangaArtistOutline,
        binding.thumbnailUrlOutline,
        binding.mangaDescriptionOutline,
    ).forEach {
        it.boxStrokeColor = colors.iconColor

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            it.cursorColor = ColorStateList.valueOf(colors.iconColor)
        }
    }

    binding.autofillFromTracker.setTextColor(colors.btnTextColor)
    binding.autofillFromTracker.setBackgroundColor(colors.btnBgColor)
    binding.resetTags.setTextColor(colors.btnTextColor)
    binding.resetTags.setBackgroundColor(colors.btnBgColor)
    binding.resetInfo.setTextColor(colors.btnTextColor)
    binding.resetInfo.setBackgroundColor(colors.btnBgColor)
    // KMK <--

    binding.resetTags.setOnClickListener { resetTags(anime, binding, scope, colors) }
    binding.resetInfo.setOnClickListener { resetInfo(anime, binding, scope, colors) }
    binding.autofillFromTracker.setOnClickListener {
        scope.launch {
            getTrackers(anime, binding, context, getTracks, trackerManager, tracks, showTrackerSelectionDialogue)
        }
    }
}

private suspend fun getTrackers(anime: Anime, binding: EditMangaDialogBinding, context: Context, getTracks: GetTracks, trackerManager: TrackerManager, tracks: MutableState<List<Pair<Track, Tracker>>>, showTrackerSelectionDialogue: MutableState<Boolean>) {
    tracks.value = getTracks.await(anime.id).map { track ->
        track to trackerManager.get(track.trackerId)!!
    }
        .filterNot { (_, tracker) -> tracker is EnhancedTracker }

    if (tracks.value.isEmpty()) {
        context.toast(context.stringResource(SYMR.strings.entry_not_tracked))
        return
    }

    if (tracks.value.size > 1) {
        showTrackerSelectionDialogue.value = true
        return
    }

    autofillFromTracker(binding, tracks.value.first().first, tracks.value.first().second)
}

private fun setTextIfNotBlank(field: (String) -> Unit, value: String?) {
    value?.takeIf { it.isNotBlank() }?.let { field(it) }
}

private suspend fun autofillFromTracker(binding: EditMangaDialogBinding, track: Track, tracker: Tracker) {
    try {
        val trackerMangaMetadata = tracker.getMangaMetadata(track)

        setTextIfNotBlank(binding.title::setText, trackerMangaMetadata?.title)
        setTextIfNotBlank(binding.mangaAuthor::setText, trackerMangaMetadata?.authors)
        setTextIfNotBlank(binding.mangaArtist::setText, trackerMangaMetadata?.artists)
        setTextIfNotBlank(binding.thumbnailUrl::setText, trackerMangaMetadata?.thumbnailUrl)
        setTextIfNotBlank(binding.mangaDescription::setText, trackerMangaMetadata?.description)
    } catch (e: Throwable) {
        tracker.logcat(LogPriority.ERROR, e)
        binding.root.context.toast(
            binding.root.context.stringResource(
                MR.strings.track_error,
                tracker.name,
                e.message ?: "",
            ),
        )
    }
}

private fun resetTags(
    anime: Anime,
    binding: EditMangaDialogBinding,
    scope: CoroutineScope,
    // KMK -->
    colors: EditAnimeDialogColors,
    // KMK <--
) {
    if (anime.genre.isNullOrEmpty() || anime.isLocal()) {
        binding.mangaGenresTags.setChips(emptyList(), scope, colors)
    } else {
        binding.mangaGenresTags.setChips(anime.ogGenre.orEmpty(), scope, colors)
    }
}

private fun loadCover(
    anime: Anime,
    binding: EditMangaDialogBinding,
    // KMK -->
    coverRatio: MutableFloatState,
    // KMK <--
) {
    // KMK -->
    if (Injekt.get<UiPreferences>().usePanoramaCoverAlways().get() && coverRatio.floatValue <= RatioSwitchToPanorama) {
        binding.mangaCover.visibility = View.GONE
        binding.mangaCoverPanorama.visibility = View.VISIBLE
        binding.mangaCoverPanorama.load(anime) {
            transformations(RoundedCornersTransformation(4.dpToPx.toFloat()))
        }
    } else {
        // KMK <--
        binding.mangaCover.load(anime) {
            transformations(RoundedCornersTransformation(4.dpToPx.toFloat()))
        }
    }
}

private fun resetInfo(
    anime: Anime,
    binding: EditMangaDialogBinding,
    scope: CoroutineScope,
    // KMK -->
    colors: EditAnimeDialogColors,
    // KMK <--
) {
    binding.title.text?.clear()
    binding.mangaAuthor.text?.clear()
    binding.mangaArtist.text?.clear()
    binding.thumbnailUrl.text?.clear()
    binding.mangaDescription.text?.clear()
    resetTags(anime, binding, scope, colors)
}

private fun ChipGroup.setChips(
    items: List<String>,
    scope: CoroutineScope,
    // KMK -->
    colors: EditAnimeDialogColors,
    // KMK <--
) {
    removeAllViews()

    // KMK -->
    val colorStateList = ColorStateList.valueOf(colors.tagColor)
    // KMK <--

    items.asSequence().map { item ->
        Chip(context).apply {
            text = item
            // KMK -->
            setTextColor(colors.tagTextColor)
            // KMK <--

            isCloseIconVisible = true
            // KMK -->
            // closeIcon?.setTint(context.getResourceColor(R.attr.colorAccent))
            closeIcon?.setTint(colors.iconColor)
            // KMK <--
            setOnCloseIconClickListener {
                removeView(this)
            }

            // KMK -->
            chipBackgroundColor = colorStateList
            // KMK <--
        }
    }.forEach {
        addView(it)
    }

    val addTagChip = Chip(context).apply {
        text = SYMR.strings.add_tags.getString(context)
        // KMK -->
        setTextColor(colors.tagTextColor)
        // KMK <--

        chipIcon = ContextCompat.getDrawable(context, R.drawable.ic_add_24dp)?.apply {
            isChipIconVisible = true
            // KMK -->
            // setTint(context.getResourceColor(R.attr.colorAccent))
            setTint(colors.iconColor)
            // KMK <--
        }

        // KMK -->
        chipBackgroundColor = colorStateList
        // KMK <--

        setOnClickListener {
            // KMK -->
            var dialog: AlertDialog? = null

            val builder = MaterialAlertDialogBuilder(context)
            val binding = builder.binding(context)
                .setTitle(SYMR.strings.add_tags.getString(context))
                .setHint(SYMR.strings.multi_tags_comma_separated.getString(context))
                .setPositiveButton(MR.strings.action_ok.getString(context)) {
                    dialog?.dismissDialog()
                    // KMK <--
                    val newTags = it.trimOrNull()
                    newTags?.let { tags ->
                        setChips(items + tags.split(",").mapNotNull { tag -> tag.trimOrNull() }, scope, colors)
                    }
                    // KMK -->
                }
                .setNegativeButton(MR.strings.action_cancel.getString(context)) {
                    dialog?.dismissDialog()
                }
                .setTextEdit()
                .setColors(colors)

            dialog = builder.create()
            dialog.setView(binding.root)
            dialog.show()
            // KMK <--
        }
    }
    addView(addTagChip)
}

private fun ChipGroup.getTextStrings(): List<String> = children.mapNotNull {
    if (it is Chip && !it.text.toString().contains(context.stringResource(SYMR.strings.add_tags), ignoreCase = true)) {
        it.text.toString()
    } else {
        null
    }
}.toList()

// KMK -->
private class SpinnerAdapter(
    context: Context,
    @LayoutRes val resource: Int,
    objects: List<String>,
    val colors: EditAnimeDialogColors,
) : ArrayAdapter<String>(context, resource, objects) {
    private val mInflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(mInflater, position, convertView, parent, resource)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createViewFromResource(mInflater, position, convertView, parent, resource)
    }

    private fun createViewFromResource(
        inflater: LayoutInflater,
        position: Int,
        convertView: View?,
        parent: ViewGroup,
        resource: Int,
    ): View {
        val text: TextView

        val view = convertView ?: inflater.inflate(resource, parent, false)

        try {
            //  If no custom field is assigned, assume the whole resource is a TextView
            text = view as TextView
        } catch (e: ClassCastException) {
            throw IllegalStateException("ArrayAdapter requires the resource ID to be a TextView", e)
        }

        val item: String? = getItem(position)
        if (item != null) text.text = item

        text.setTextColor(colors.textColor)
        text.setBackgroundColor(colors.dropdownBgColor)

        return view
    }
}
// KMK <--
