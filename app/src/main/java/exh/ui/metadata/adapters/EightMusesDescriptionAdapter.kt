package exh.ui.metadata.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.DescriptionAdapter8mBinding
import eu.kanade.tachiyomi.ui.base.controller.pushController
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.system.copyToClipboard
import exh.metadata.bindDrawable
import exh.metadata.metadata.EightMusesSearchMetadata
import exh.metadata.metadata.base.RaisedSearchMetadata
import exh.ui.metadata.MetadataViewController

class EightMusesDescriptionAdapter(
    private val controller: MangaController,
) :
    RecyclerView.Adapter<EightMusesDescriptionAdapter.EightMusesDescriptionViewHolder>() {

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
            val meta = controller.presenter.meta.value
            if (meta == null || meta !is EightMusesSearchMetadata) return

            binding.title.text = meta.title ?: itemView.context.getString(R.string.unknown)

            binding.moreInfo.bindDrawable(itemView.context, R.drawable.ic_info_24dp)

            binding.title.setOnLongClickListener {
                itemView.context.copyToClipboard(
                    binding.title.text.toString(),
                    binding.title.text.toString(),
                )
                true
            }

            binding.moreInfo.setOnClickListener {
                controller.router?.pushController(
                    MetadataViewController(
                        controller.manga,
                    ),
                )
            }
        }
    }
}

@Composable
fun EightMusesDescription(controller: MangaController) {
    val meta by controller.presenter.meta.collectAsState()
    EightMusesDescription(controller = controller, meta = meta)
}

@Composable
private fun EightMusesDescription(controller: MangaController, meta: RaisedSearchMetadata?) {
    val context = LocalContext.current
    AndroidView(
        factory = { factoryContext ->
            DescriptionAdapter8mBinding.inflate(LayoutInflater.from(factoryContext)).root
        },
        update = {
            if (meta == null || meta !is EightMusesSearchMetadata) return@AndroidView
            val binding = DescriptionAdapter8mBinding.bind(it)

            binding.title.text = meta.title ?: context.getString(R.string.unknown)

            binding.moreInfo.bindDrawable(context, R.drawable.ic_info_24dp)

            binding.title.setOnLongClickListener {
                context.copyToClipboard(
                    binding.title.text.toString(),
                    binding.title.text.toString(),
                )
                true
            }

            binding.moreInfo.setOnClickListener {
                controller.router?.pushController(
                    MetadataViewController(
                        controller.manga,
                    ),
                )
            }
        },
    )
}
