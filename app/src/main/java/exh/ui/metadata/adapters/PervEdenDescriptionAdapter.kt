package exh.ui.metadata.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterPeBinding
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.MetadataUtil
import exh.metadata.bindDrawable
import exh.metadata.metadata.PervEdenSearchMetadata
import exh.ui.metadata.MetadataViewController
import java.util.Locale
import kotlin.math.round

class PervEdenDescriptionAdapter(
    private val controller: MangaController,
) :
    RecyclerView.Adapter<PervEdenDescriptionAdapter.PervEdenDescriptionViewHolder>() {

    private lateinit var binding: DescriptionAdapterPeBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PervEdenDescriptionViewHolder {
        binding = DescriptionAdapterPeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PervEdenDescriptionViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: PervEdenDescriptionViewHolder, position: Int) {
        holder.bind()
    }

    inner class PervEdenDescriptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            val meta = controller.presenter.meta
            if (meta == null || meta !is PervEdenSearchMetadata) return

            binding.genre.text = meta.genre?.let { MetadataUtil.getGenreAndColour(itemView.context, it) }?.let {
                binding.genre.setBackgroundColor(it.first)
                it.second
            } ?: meta.genre ?: itemView.context.getString(R.string.unknown)

            val language = meta.lang
            binding.language.text = if (language != null) {
                val local = Locale(language)
                local.displayName
            } else itemView.context.getString(R.string.unknown)

            binding.ratingBar.rating = meta.rating ?: 0F
            @SuppressLint("SetTextI18n")
            binding.rating.text = (round((meta.rating ?: 0F) * 100.0) / 100.0).toString() + " - " + MetadataUtil.getRatingString(itemView.context, meta.rating?.times(2))

            binding.moreInfo.bindDrawable(itemView.context, R.drawable.ic_info_24dp)

            listOf(
                binding.genre,
                binding.language,
                binding.rating,
            ).forEach { textView ->
                textView.setOnLongClickListener {
                    itemView.context.copyToClipboard(
                        textView.text.toString(),
                        textView.text.toString(),
                    )
                    true
                }
            }

            binding.moreInfo.setOnClickListener {
                controller.router?.pushController(
                    MetadataViewController(
                        controller.manga,
                    ).withFadeTransaction(),
                )
            }
        }
    }
}
