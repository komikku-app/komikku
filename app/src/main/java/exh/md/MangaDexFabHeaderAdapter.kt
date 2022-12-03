package exh.md

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.tachiyomi.databinding.SourceFilterMangadexHeaderBinding
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.online.RandomMangaSource
import eu.kanade.tachiyomi.ui.browse.source.browse.BrowseSourceScreen
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.lang.withIOContext
import exh.md.follows.MangaDexFollowsScreen

class MangaDexFabHeaderAdapter(val navigator: Navigator, val source: CatalogueSource, val onClick: () -> Unit) :
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
                navigator.replace(MangaDexFollowsScreen(source.id))
                onClick()
            }
            binding.mangadexRandom.setOnClickListener {
                launchUI {
                    val randomMangaUrl = withIOContext {
                        (source as? RandomMangaSource)?.fetchRandomMangaUrl()
                    }
                    navigator.replace(
                        BrowseSourceScreen(
                            source.id,
                            "id:$randomMangaUrl",
                        ),
                    )
                    onClick()
                }
            }
        }
    }
}
