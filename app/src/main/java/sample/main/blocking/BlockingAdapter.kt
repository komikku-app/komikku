package sample.main.blocking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import eu.kanade.tachiyomi.R

/**
 * Created by Edsuns@qq.com on 2021/2/27.
 */
class BlockedListAdapter(private val layoutInflater: LayoutInflater) :
    RecyclerView.Adapter<BlockedRequestViewHolder>() {

    var data: LinkedHashMap<String, String> = LinkedHashMap(0)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockedRequestViewHolder {
        val itemView = layoutInflater.inflate(R.layout.blocked_request_item, parent, false)
        return BlockedRequestViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: BlockedRequestViewHolder, position: Int) {
        val blockedRequest = data.getAt(position)
        holder.blockedUrl.text = blockedRequest?.key
        holder.matchedRule.text = blockedRequest?.value
    }

    override fun getItemCount(): Int = data.size

    private fun LinkedHashMap<String, String>.getAt(index: Int): Map.Entry<String, String>? {
        var i = 0
        forEach {
            if (i == index) {
                return it
            }
            i++
        }
        return null
    }
}

class BlockedRequestViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val blockedUrl: TextView = itemView.findViewById(R.id.blockedUrl)
    val matchedRule: TextView = itemView.findViewById(R.id.matchedRule)
}
