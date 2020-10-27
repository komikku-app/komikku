package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterMdBinding
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.MetadataUtil.getRatingString
import exh.metadata.bindDrawable
import exh.metadata.metadata.MangaDexSearchMetadata
import exh.ui.metadata.MetadataViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.android.view.longClicks
import kotlin.math.round

class MangaDexDescriptionAdapter(
    private val controller: MangaController
) :
    RecyclerView.Adapter<MangaDexDescriptionAdapter.MangaDexDescriptionViewHolder>() {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private lateinit var binding: DescriptionAdapterMdBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MangaDexDescriptionViewHolder {
        binding = DescriptionAdapterMdBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MangaDexDescriptionViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: MangaDexDescriptionViewHolder, position: Int) {
        holder.bind()
    }

    inner class MangaDexDescriptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        fun bind() {
            val meta = controller.presenter.meta
            if (meta == null || meta !is MangaDexSearchMetadata) return

            val ratingFloat = meta.rating?.toFloatOrNull()
            binding.ratingBar.rating = ratingFloat?.div(2F) ?: 0F
            @SuppressLint("SetTextI18n")
            binding.rating.text = (round((meta.rating?.toFloatOrNull() ?: 0F) * 100.0) / 100.0).toString() + " - " + getRatingString(itemView.context, ratingFloat)

            binding.moreInfo.bindDrawable(itemView.context, R.drawable.ic_info_24dp)

            binding.rating.longClicks()
                .onEach {
                    itemView.context.copyToClipboard(
                        binding.rating.text.toString(),
                        binding.rating.text.toString()
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
