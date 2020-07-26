package eu.kanade.tachiyomi.ui.manga.info

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.MangaInfoButtonsBinding
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
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
                binding.recommendBtn.visibleIf { !preferences.recommendsInOverflow().get() }
                binding.recommendBtn.clicks()
                    .onEach { controller.openRecommends() }
                    .launchIn(scope)
            } else {
                if (controller.smartSearchConfig.origMangaId != null) {
                    binding.mergeBtn.visible()
                }
                binding.mergeBtn.clicks()
                    .onEach {
                        controller.mergeWithAnother()
                    }

                    .launchIn(scope)
            }
            // EXH <--
        }
    }
}
