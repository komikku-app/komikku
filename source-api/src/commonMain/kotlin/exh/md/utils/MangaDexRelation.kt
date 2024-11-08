package exh.md.utils

import dev.icerock.moko.resources.StringResource
import tachiyomi.i18n.sy.SYMR

enum class MangaDexRelation(val res: StringResource, val mdString: String?) {
    SIMILAR(SYMR.strings.relation_similar, null),
    MONOCHROME(SYMR.strings.relation_monochrome, "monochrome"),
    MAIN_STORY(SYMR.strings.relation_main_story, "main_story"),
    ADAPTED_FROM(SYMR.strings.relation_adapted_from, "adapted_from"),
    BASED_ON(SYMR.strings.relation_based_on, "based_on"),
    PREQUEL(SYMR.strings.relation_prequel, "prequel"),
    SIDE_STORY(SYMR.strings.relation_side_story, "side_story"),
    DOUJINSHI(SYMR.strings.relation_doujinshi, "doujinshi"),
    SAME_FRANCHISE(SYMR.strings.relation_same_franchise, "same_franchise"),
    SHARED_UNIVERSE(SYMR.strings.relation_shared_universe, "shared_universe"),
    SEQUEL(SYMR.strings.relation_sequel, "sequel"),
    SPIN_OFF(SYMR.strings.relation_spin_off, "spin_off"),
    ALTERNATE_STORY(SYMR.strings.relation_alternate_story, "alternate_story"),
    PRESERIALIZATION(SYMR.strings.relation_preserialization, "preserialization"),
    COLORED(SYMR.strings.relation_colored, "colored"),
    SERIALIZATION(SYMR.strings.relation_serialization, "serialization"),
    ALTERNATE_VERSION(SYMR.strings.relation_alternate_version, "alternate_version"),
    ;

    companion object {
        fun fromDex(mdString: String) = entries.find { it.mdString == mdString }
    }
}
