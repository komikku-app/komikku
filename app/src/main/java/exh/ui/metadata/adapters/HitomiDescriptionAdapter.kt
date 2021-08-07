package exh.ui.metadata.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapterHiBinding
import eu.kanade.tachiyomi.ui.base.controller.withFadeTransaction
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.MetadataUtil
import exh.metadata.bindDrawable
import exh.metadata.metadata.HitomiSearchMetadata
import exh.ui.metadata.MetadataViewController
import java.util.Date

class HitomiDescriptionAdapter(
    private val controller: MangaController
) :
    RecyclerView.Adapter<HitomiDescriptionAdapter.HitomiDescriptionViewHolder>() {

    private lateinit var binding: DescriptionAdapterHiBinding

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HitomiDescriptionViewHolder {
        binding = DescriptionAdapterHiBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HitomiDescriptionViewHolder(binding.root)
    }

    override fun getItemCount(): Int = 1

    override fun onBindViewHolder(holder: HitomiDescriptionViewHolder, position: Int) {
        holder.bind()
    }

    inner class HitomiDescriptionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            val meta = controller.presenter.meta
            if (meta == null || meta !is HitomiSearchMetadata) return

            binding.genre.text = meta.genre?.let { MetadataUtil.getGenreAndColour(itemView.context, it) }?.let {
                binding.genre.setBackgroundColor(it.first)
                it.second
            } ?: meta.genre ?: itemView.context.getString(R.string.unknown)

            binding.whenPosted.text = MetadataUtil.EX_DATE_FORMAT.format(Date(meta.uploadDate ?: 0))
            binding.language.text = meta.language ?: itemView.context.getString(R.string.unknown)

            binding.moreInfo.bindDrawable(itemView.context, R.drawable.ic_info_24dp)

            listOf(
                binding.genre,
                binding.language,
                binding.whenPosted
            ).forEach { textView ->
                textView.setOnLongClickListener {
                    itemView.context.copyToClipboard(
                        textView.text.toString(),
                        textView.text.toString()
                    )
                    true
                }
            }

            binding.moreInfo.setOnClickListener {
                controller.router?.pushController(
                    MetadataViewController(
                        controller.manga
                    ).withFadeTransaction()
                )
            }
        }
    }
}
