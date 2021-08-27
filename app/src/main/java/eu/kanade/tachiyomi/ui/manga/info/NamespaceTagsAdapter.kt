package eu.kanade.tachiyomi.ui.manga.info

import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.manga.MangaController

class NamespaceTagsAdapter(val controller: MangaController, val source: Source) :
    FlexibleAdapter<NamespaceTagsItem>(null, controller, true)
