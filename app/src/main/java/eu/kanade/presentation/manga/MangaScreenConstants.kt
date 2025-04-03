package eu.kanade.presentation.manga

enum class DownloadAction {
    NEXT_1_EPISODE,
    NEXT_5_EPISODES,
    NEXT_10_EPISODES,
    NEXT_25_EPISODES,
    UNSEEN_EPISODES,
}

enum class EditCoverAction {
    EDIT,
    DELETE,
}

enum class MangaScreenItem {
    INFO_BOX,
    ACTION_ROW,

    // SY -->
    METADATA_INFO,

    // SY <--
    DESCRIPTION_WITH_TAG,

    // SY -->
    INFO_BUTTONS,
    EPISODE_PREVIEW_LOADING,
    EPISODE_PREVIEW_ROW,
    EPISODE_PREVIEW_MORE,
    // SY <--

    EPISODE_HEADER,
    EPISODE,

    // KMK -->
    RELATED_MANGAS,
    // KMK <--
}
