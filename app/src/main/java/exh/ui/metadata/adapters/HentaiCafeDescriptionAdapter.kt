package exh.ui.metadata.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterHcBinding
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.metadata.HentaiCafeSearchMetadata
import exh.ui.metadata.MetadataViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.android.view.longClicks

class HentaiCafeDescriptionAdapter(
    private val controller: MangaController
) :
    RecyclerView.Adapter<HentaiCafeDescriptionAdapter.HentaiCafeDescriptionViewHolder>() {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private lateinit var binding: DescriptionAdapterHcBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HentaiCafeDescriptionViewHolder {
        binding = DescriptionAdapterHcBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HentaiCafeDescriptionViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: HentaiCafeDescriptionViewHolder, position: Int) {
        holder.bind()
    }

    inner class HentaiCafeDescriptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            val meta = controller.presenter.meta
            if (meta == null || meta !is HentaiCafeSearchMetadata) return

            binding.artist.text = meta.artist ?: itemView.context.getString(R.string.unknown)

            binding.artist.longClicks()
                .onEach {
                    itemView.context.copyToClipboard(
                        binding.artist.text.toString(),
                        binding.artist.text.toString()
                    )
                }
                .launchIn(scope)

            binding.moreInfo.clicks()
                .onEach {
                    controller.router?.pushController(
                        MetadataViewController(
                            controller.manga
                        ).withFadeTransaction()
                    )
                }
                .launchIn(scope)
        }
    }
}
