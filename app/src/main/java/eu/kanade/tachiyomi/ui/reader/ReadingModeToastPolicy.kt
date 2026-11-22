package eu.kanade.tachiyomi.ui.reader

// KMK -->
/**
 * Whether the "Reading webtoon style" snackbar shown when a chapter opens should be displayed.
 * It must respect the "show reading mode" setting, like the regular reading mode toast does.
 */
internal fun shouldShowAutoWebtoonToast(showReadingMode: Boolean, isAutoWebtoon: Boolean): Boolean {
    return showReadingMode && isAutoWebtoon
}
// KMK <--
