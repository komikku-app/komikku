package exh.md

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bluelinelabs.conductor.Controller
import eu.kanade.tachiyomi.databinding.SourceFilterMangadexHeaderBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.RandomMangaSource
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import exh.md.follows.MangaDexFollowsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks

class MangaDexFabHeaderAdapter(val controller: Controller, val source: CatalogueSource) :
    RecyclerView.Adapter<MangaDexFabHeaderAdapter.SavedSearchesViewHolder>() {

    private lateinit var binding: SourceFilterMangadexHeaderBinding

    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedSearchesViewHolder {
        binding = SourceFilterMangadexHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SavedSearchesViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: SavedSearchesViewHolder, position: Int) {
        holder.bind()
    }

    inner class SavedSearchesViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            binding.mangadexFollows.clicks()
                .onEach {
                    controller.router.replaceTopController(MangaDexFollowsController(source).withFadeTransaction())
                }
                .launchIn(scope)
            binding.mangadexRandom.clicks()
                .onEach {
                    (source as? RandomMangaSource)?.fetchRandomMangaUrl()?.let { randomMangaId ->
                        controller.router.replaceTopController(BrowseSourceController(source, randomMangaId).withFadeTransaction())
                    }
                }
                .launchIn(scope)
        }
    }
}
