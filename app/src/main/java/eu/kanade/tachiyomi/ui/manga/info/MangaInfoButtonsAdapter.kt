package eu.kanade.tachiyomi.ui.manga.info

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MangaInfoButtonsBinding
import eu.kanade.tachiyomi.ui.manga.MangaController
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class MangaInfoButtonsAdapter(
    private val controller: MangaController
) :
    RecyclerView.Adapter<MangaInfoButtonsAdapter.HeaderViewHolder>() {

    private val preferences: PreferencesHelper by injectLazy()

    private lateinit var binding: MangaInfoButtonsBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderViewHolder {
        binding = MangaInfoButtonsBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HeaderViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: HeaderViewHolder, position: Int) {
        holder.bind()
    }

    inner class HeaderViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            // EXH -->
            if (controller.smartSearchConfig == null) {
                binding.recommendBtn.isVisible = !preferences.recommendsInOverflow().get()
                binding.recommendBtn.clicks()
                    .onEach { controller.openRecommends() }
                    .launchIn(controller.viewScope)
            } else {
                if (controller.smartSearchConfig.origMangaId != null) {
                    binding.mergeBtn.isVisible = true
                }
                binding.mergeBtn.clicks()
                    .onEach {
                        controller.mergeWithAnother()
                    }
                    .launchIn(controller.viewScope)
            }
            // EXH <--
        }
    }
}
