package exh.md

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.databinding.SourceFilterMangadexHeaderBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.RandomMangaSource
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.md.follows.MangaDexFollowsController
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks

class MangaDexFabHeaderAdapter(val controller: BaseController<*>, val source: CatalogueSource, val onClick: () -> Unit) :
    RecyclerView.Adapter<MangaDexFabHeaderAdapter.SavedSearchesViewHolder>() {

    private lateinit var binding: SourceFilterMangadexHeaderBinding

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
            binding.mangadexFollows.setOnClickListener {
                controller.router.replaceTopController(MangaDexFollowsController(source).withFadeTransaction())
                onClick()
            }
            binding.mangadexRandom.clicks()
                .onEach {
                    val randomMangaUrl = withIOContext {
                        (source as? RandomMangaSource)?.fetchRandomMangaUrl()
                    }
                    controller.router.replaceTopController(BrowseSourceController(source, randomMangaUrl).withFadeTransaction())
                    onClick()
                }.launchIn(controller.viewScope)
        }
    }
}
