package sample.settings

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R
import io.github.edsuns.adfilter.AdFilter
import io.github.edsuns.adfilter.DownloadState
import io.github.edsuns.adfilter.Filter
import io.github.edsuns.adfilter.FilterViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Created by Edsuns@qq.com on 2021/2/28.
 */
class FilterListAdapter(
    private val context: Context,
    private val layoutInflater: LayoutInflater,
) : RecyclerView.Adapter<FilterViewHolder>(),
    DialogInterface.OnClickListener {
    val viewModel: FilterViewModel = AdFilter.get(context).viewModel

    private lateinit var filterList: List<Filter>

    var data
        get() = filterList

        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            filterList = value
            notifyDataSetChanged()
        }

    private var selectedFilter: Filter? = null

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH)

    private fun getString(resId: Int): String = context.getString(resId)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): FilterViewHolder {
        val itemView = layoutInflater.inflate(R.layout.filter_item, parent, false)
        return FilterViewHolder(this, itemView)
    }

    override fun onBindViewHolder(
        holder: FilterViewHolder,
        position: Int,
    ) {
        filterList[position].let { filter ->
            holder.filterName.text = filter.name.ifBlank { filter.url }
            holder.filterUrl.text = filter.url
            holder.switch.isChecked = filter.isEnabled
            holder.switch.isEnabled = filter.filtersCount > 0
            holder.filterUpdateTime.text = when (filter.downloadState) {
                DownloadState.ENQUEUED -> getString(R.string.waiting)
                DownloadState.DOWNLOADING -> getString(R.string.downloading)
                DownloadState.INSTALLING -> getString(R.string.installing)
                DownloadState.FAILED -> getString(R.string.failed_to_download)
                DownloadState.CANCELLED -> getString(R.string.cancelled)
                else -> {
                    if (filter.hasDownloaded()) {
                        dateFormatter.format(Date(filter.updateTime))
                    } else {
                        getString(R.string.not_downloaded)
                    }
                }
            }
            holder.filtersCount.text =
                if (filter.hasDownloaded()) filter.filtersCount.toString() else ""
            holder.itemView.setOnClickListener {
                selectedFilter = filter
                val items = context.resources.getTextArray(R.array.filter_detail_items)
                if (filter.downloadState.isRunning) {
                    items[2] = getString(R.string.cancel)
                }
                AlertDialog
                    .Builder(context)
                    .setItems(items, this)
                    .show()
            }
        }
    }

    override fun getItemCount(): Int = filterList.size

    override fun onClick(
        dialog: DialogInterface?,
        which: Int,
    ) {
        selectedFilter?.let {
            when (which) {
                0 -> {
                    val renameDialogView: View = layoutInflater.inflate(
                        R.layout.dialog_rename_filter,
                        LinearLayout(context),
                    )
                    val renameEdit: EditText = renameDialogView.findViewById(R.id.renameEdit)
                    renameEdit.setText(it.name)
                    AlertDialog
                        .Builder(context)
                        .setTitle(R.string.rename_filter)
                        .setMessage(it.url)
                        .setView(renameDialogView)
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            viewModel.renameFilter(it.id, renameEdit.text.toString())
                        }.show()
                }
                1 -> {
                    val clipboardManager =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = ClipData.newPlainText("text", it.url)
                    clipboardManager.setPrimaryClip(clipData)
                    Toast.makeText(context, R.string.url_copied, Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    if (it.downloadState.isRunning) {
                        viewModel.cancelDownload(it.id)
                    } else {
                        viewModel.download(it.id)
                    }
                }
                3 -> viewModel.removeFilter(it.id)
                else -> return
            }
        }
    }
}

class FilterViewHolder(
    private val adapter: FilterListAdapter,
    itemView: View,
) : RecyclerView.ViewHolder(itemView) {
    val filterName: TextView = itemView.findViewById(R.id.filterName)
    val filterUrl: TextView = itemView.findViewById(R.id.filterUrl)
    val filterUpdateTime: TextView = itemView.findViewById(R.id.filterUpdateTime)
    val filtersCount: TextView = itemView.findViewById(R.id.filtersCount)
    val switch: SwitchCompat = itemView.findViewById(R.id.filterSwitch)

    init {
        switch.setOnCheckedChangeListener { _, isChecked ->
            adapter.viewModel.setFilterEnabled(adapter.data[bindingAdapterPosition].id, isChecked)
        }
    }
}
