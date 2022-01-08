package exh.md.utils

import androidx.annotation.StringRes
import eu.kanade.tachiyomi.R

enum class MangaDexRelation(@StringRes val resId: Int, val mdString: String?) {
    SIMILAR(R.string.relation_similar, null),
    MONOCHROME(R.string.relation_monochrome, "monochrome"),
    MAIN_STORY(R.string.relation_main_story, "main_story"),
    ADAPTED_FROM(R.string.relation_adapted_from, "adapted_from"),
    BASED_ON(R.string.relation_based_on, "based_on"),
    PREQUEL(R.string.relation_prequel, "prequel"),
    SIDE_STORY(R.string.relation_side_story, "side_story"),
    DOUJINSHI(R.string.relation_doujinshi, "doujinshi"),
    SAME_FRANCHISE(R.string.relation_same_franchise, "same_franchise"),
    SHARED_UNIVERSE(R.string.relation_shared_universe, "shared_universe"),
    SEQUEL(R.string.relation_sequel, "sequel"),
    SPIN_OFF(R.string.relation_spin_off, "spin_off"),
    ALTERNATE_STORY(R.string.relation_alternate_story, "alternate_story"),
    PRESERIALIZATION(R.string.relation_preserialization, "preserialization"),
    COLORED(R.string.relation_colored, "colored"),
    SERIALIZATION(R.string.relation_serialization, "serialization"),
    ALTERNATE_VERSION(R.string.relation_alternate_version, "alternate_version");

    companion object {
        fun fromDex(mdString: String) = values().find { it.mdString == mdString }
    }
}
