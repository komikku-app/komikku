package eu.kanade.tachiyomi.ui.extension

import android.view.View
import androidx.core.view.isVisible
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.extension.api.ExtensionGithubApi
import eu.kanade.tachiyomi.extension.model.Extension
import eu.kanade.tachiyomi.extension.model.InstallStep
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.ui.base.holder.SlicedHolder
import eu.kanade.tachiyomi.util.system.LocaleHelper
import eu.kanade.tachiyomi.util.system.getResourceColor
import io.github.mthli.slice.Slice
import kotlinx.android.synthetic.main.extension_card_item.cancel_button
import kotlinx.android.synthetic.main.extension_card_item.card
import kotlinx.android.synthetic.main.extension_card_item.ext_button
import kotlinx.android.synthetic.main.extension_card_item.ext_title
import kotlinx.android.synthetic.main.extension_card_item.image
import kotlinx.android.synthetic.main.extension_card_item.lang
import kotlinx.android.synthetic.main.extension_card_item.version

class ExtensionHolder(view: View, override val adapter: ExtensionAdapter) :
    BaseFlexibleViewHolder(view, adapter),
    SlicedHolder {

    override val slice = Slice(card).apply {
        setColor(adapter.cardBackground)
    }

    override val viewToSlice: View
        get() = card

    init {
        ext_button.setOnClickListener {
            adapter.buttonClickListener.onButtonClick(bindingAdapterPosition)
        }
        cancel_button.setOnClickListener {
            adapter.buttonClickListener.onCancelButtonClick(bindingAdapterPosition)
        }
    }

    fun bind(item: ExtensionItem) {
        val extension = item.extension
        setCardEdges(item)

        // Set source name
        ext_title.text = extension.name
        version.text = extension.versionName
        lang.text = if (extension !is Extension.Untrusted) {
            LocaleHelper.getSourceDisplayName(extension.lang, itemView.context)
        } else {
            itemView.context.getString(R.string.ext_untrusted).toUpperCase()
        }

        GlideApp.with(itemView.context).clear(image)
        if (extension is Extension.Available) {
            GlideApp.with(itemView.context)
                .load(extension.iconUrl)
                .into(image)
        } else {
            extension.getApplicationIcon(itemView.context)?.let { image.setImageDrawable(it) }
        }
        bindButtons(item)
    }

    @Suppress("ResourceType")
    fun bindButtons(item: ExtensionItem) = with(ext_button) {
        setTextColor(context.getResourceColor(R.attr.colorAccent))

        val extension = item.extension

        val installStep = item.installStep
        setText(
            when (installStep) {
                InstallStep.Pending -> context.getString(R.string.ext_pending)
                InstallStep.Downloading -> context.getString(R.string.ext_downloading)
                InstallStep.Installing -> context.getString(R.string.ext_installing)
                InstallStep.Installed -> context.getString(R.string.ext_installed)
                InstallStep.Error -> context.getString(R.string.action_retry)
                InstallStep.Idle -> {
                    when (extension) {
                        is Extension.Installed -> {
                            when {
                                extension.hasUpdate -> {
                                    context.getString(R.string.ext_update)
                                }
                                extension.isObsolete -> {
                                    setTextColor(context.getResourceColor(R.attr.colorError))
                                    context.getString(R.string.ext_obsolete)
                                }
                                extension.isUnofficial -> {
                                    setTextColor(context.getResourceColor(R.attr.colorError))
                                    context.getString(R.string.ext_unofficial)
                                }
                                extension.isRedundant -> {
                                    setTextColor(context.getResourceColor(R.attr.colorError))
                                    context.getString(R.string.ext_redundant)
                                }
                                else -> {
                                    context.getString(R.string.ext_details).plusRepo(extension)
                                }
                            }
                        }
                        is Extension.Untrusted -> context.getString(R.string.ext_trust)
                        is Extension.Available -> context.getString(R.string.ext_install)
                    }
                }
            }
        )

        val isIdle = installStep == InstallStep.Idle || installStep == InstallStep.Error
        cancel_button.isVisible = !isIdle
        isEnabled = isIdle
        isClickable = isIdle
    }

    // SY -->
    private fun String.plusRepo(extension: Extension): String {
        return if (extension is Extension.Available) {
            when (extension.repoUrl) {
                ExtensionGithubApi.REPO_URL_PREFIX -> this
                else -> {
                    this + if (this.isEmpty()) {
                        ""
                    } else {
                        " • "
                    } + itemView.context.getString(R.string.repo_source)
                }
            }
        } else this
    }
    // SY <--
}
