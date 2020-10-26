package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterMdBinding
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
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
import kotlin.math.roundToInt

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

            val ratingFloat = meta.rating?.toFloatOrNull()?.div(2F)
            val name = when (((ratingFloat ?: 100F) * 2).roundToInt()) {
                0 -> R.string.rating0
                1 -> R.string.rating1
                2 -> R.string.rating2
                3 -> R.string.rating3
                4 -> R.string.rating4
                5 -> R.string.rating5
                6 -> R.string.rating6
                7 -> R.string.rating7
                8 -> R.string.rating8
                9 -> R.string.rating9
                10 -> R.string.rating10
                else -> R.string.no_rating
            }

            binding.ratingBar.rating = ratingFloat ?: 0F
            @SuppressLint("SetTextI18n")
            binding.rating.text = (round((meta.rating?.toFloatOrNull() ?: 0F) * 100.0) / 100.0).toString() + " - " + itemView.context.getString(name)

            ContextCompat.getDrawable(itemView.context, R.drawable.ic_info_24dp)?.apply {
                setTint(itemView.context.getResourceColor(R.attr.colorAccent))
                setBounds(0, 0, 20.dpToPx, 20.dpToPx)
                binding.moreInfo.setCompoundDrawables(this, null, null, null)
            }

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
