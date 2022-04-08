package eu.kanade.tachiyomi.ui.reader.setting

import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R

enum class ReaderBottomButton(val value: String, @StringRes val stringRes: Int) {
    ViewChapters("vc", R.string.action_view_chapters),
    WebView("wb", R.string.action_open_in_web_view),
    ReadingMode("rm", R.string.viewer),
    Rotation("rot", R.string.rotation_type),
    CropBordersPager("cbp", R.string.pref_crop_borders_pager),
    CropBordersContinuesVertical("cbc", R.string.pref_crop_borders_continuous_vertical),
    CropBordersWebtoon("cbw", R.string.pref_crop_borders_webtoon),
    PageLayout("pl", R.string.page_layout),
    ;

    fun isIn(buttons: Collection<String>) = value in buttons

    companion object {
        val BUTTONS_DEFAULTS = setOf(
            ViewChapters,
            WebView,
            CropBordersPager,
            CropBordersContinuesVertical,
            PageLayout,
        ).map { it.value }.toSet()
    }
}
