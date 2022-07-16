package eu.kanade.tachiyomi.data.coil

import coil.key.Keyer
import coil.request.Options
import eu.kanade.domain.manga.model.PagePreview

class PagePreviewKeyer : Keyer<PagePreview> {
    override fun key(data: PagePreview, options: Options): String {
        return data.imageUrl
    }
}
