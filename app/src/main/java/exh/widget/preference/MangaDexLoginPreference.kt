package exh.widget.preference

import android.content.Context
import android.util.AttributeSet
import androidx.core.view.isVisible
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.PrefItemMangadexBinding
import eu.kanade.tachiyomi.source.online.all.MangaDex
import eu.kanade.tachiyomi.util.system.getResourceColor

class MangaDexLoginPreference @JvmOverloads constructor(
    context: Context,
    val source: MangaDex,
    attrs: AttributeSet? = null,
) : Preference(context, attrs) {

    init {
        layoutResource = R.layout.pref_item_mangadex
    }

    var binding: PrefItemMangadexBinding? = null

    private var onLoginClick: () -> Unit = {}

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        binding = PrefItemMangadexBinding.bind(holder.itemView)
        holder.itemView.setOnClickListener {
            onLoginClick()
        }
        val loginFrame = binding?.loginFrame
        val color = if (source.isLogged()) {
            context.getResourceColor(R.attr.colorAccent)
        } else {
            context.getResourceColor(R.attr.colorSecondary)
        }

        binding?.login?.setImageResource(R.drawable.ic_outline_people_alt_24dp)
        binding?.login?.drawable?.setTint(color)

        loginFrame?.isVisible = true
        loginFrame?.setOnClickListener {
            onLoginClick()
        }
    }

    fun setOnLoginClickListener(block: () -> Unit) {
        onLoginClick = block
    }

    // Make method public
    public override fun notifyChanged() {
        super.notifyChanged()
    }
}
