package exh.eh

import exh.eh.tags.Artist
import exh.eh.tags.Artist2
import exh.eh.tags.Character
import exh.eh.tags.Cosplayer
import exh.eh.tags.Female
import exh.eh.tags.Group
import exh.eh.tags.Group2
import exh.eh.tags.Language
import exh.eh.tags.Male
import exh.eh.tags.Mixed
import exh.eh.tags.Other
import exh.eh.tags.Parody
import exh.eh.tags.Reclass

object EHTags {
    fun getAllTags(): List<String> = listOf(
        Female.getTags(),
        Male.getTags(),
        Language.getTags(),
        Reclass.getTags(),
        Mixed.getTags(),
        Other.getTags(),
        Cosplayer.getTags(),
        Parody.getTags(),
        Character.getTags(),
        Group.getTags(),
        Group2.getTags(),
        Artist.getTags(),
        Artist2.getTags(),
    ).flatten().flatten()

    fun getNamespaces(): List<String> = listOf(
        "reclass",
        "language",
        "parody",
        "character",
        "group",
        "artist",
        "cosplayer",
        "male",
        "female",
        "mixed",
        "other",
    )
}
