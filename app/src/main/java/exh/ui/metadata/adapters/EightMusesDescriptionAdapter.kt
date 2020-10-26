package exh.ui.metadata.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapter8mBinding
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import exh.metadata.metadata.EightMusesSearchMetadata
import exh.ui.metadata.MetadataViewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.android.view.longClicks

class EightMusesDescriptionAdapter(
    private val controller: MangaController
) :
    RecyclerView.Adapter<EightMusesDescriptionAdapter.EightMusesDescriptionViewHolder>() {

    private val scope = CoroutineScope(Job() + Dispatchers.Main)
    private lateinit var binding: DescriptionAdapter8mBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EightMusesDescriptionViewHolder {
        binding = DescriptionAdapter8mBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EightMusesDescriptionViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: EightMusesDescriptionViewHolder, position: Int) {
        holder.bind()
    }

    inner class EightMusesDescriptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            val meta = controller.presenter.meta
            if (meta == null || meta !is EightMusesSearchMetadata) return

            binding.title.text = meta.title ?: itemView.context.getString(R.string.unknown)

            val infoDrawable = ContextCompat.getDrawable(itemView.context, R.drawable.ic_info_24dp)
            infoDrawable?.setTint(itemView.context.getResourceColor(R.attr.colorAccent))
            infoDrawable?.setBounds(0, 0, 20.dpToPx, 20.dpToPx)
            binding.moreInfo.setCompoundDrawables(infoDrawable, null, null, null)

            binding.title.longClicks()
                .onEach {
                    itemView.context.copyToClipboard(
                        binding.title.text.toString(),
                        binding.title.text.toString()
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
