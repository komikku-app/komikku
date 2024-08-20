package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.util.system.isDevFlavor
import eu.kanade.tachiyomi.util.system.isPreviewBuildType
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR

enum class AppTheme(val titleRes: StringResource?) {
    DEFAULT(MR.strings.label_default),
    MONET(MR.strings.theme_monet),

    // Aniyomi themes
    COTTONCANDY(KMR.strings.theme_cottoncandy),
    MOCHA(KMR.strings.theme_mocha),

    GREEN_APPLE(MR.strings.theme_greenapple),
    LAVENDER(MR.strings.theme_lavender),
    MIDNIGHT_DUSK(MR.strings.theme_midnightdusk),

    //Kuukiyomi themes
    CUSTOM(KMR.strings.theme_custom),

    // TODO: re-enable for preview
    NORD(MR.strings.theme_nord.takeIf { isDevFlavor || isPreviewBuildType }),
    STRAWBERRY_DAIQUIRI(MR.strings.theme_strawberrydaiquiri),
    TAKO(MR.strings.theme_tako),
    TEALTURQUOISE(MR.strings.theme_tealturquoise),
    TIDAL_WAVE(MR.strings.theme_tidalwave),
    YINYANG(MR.strings.theme_yinyang),
    YOTSUBA(MR.strings.theme_yotsuba),

    // Aniyomi themes
    CLOUDFLARE(KMR.strings.theme_cloudflare),
    DOOM(KMR.strings.theme_doom),
    MATRIX(KMR.strings.theme_matrix),
    SAPPHIRE(KMR.strings.theme_sapphire),

    // Deprecated
    DARK_BLUE(null),
    HOT_PINK(null),
    BLUE(null),

    // SY -->
    PURE_RED(null),
    // SY <--
}
