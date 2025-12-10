package tachiyomi.data.category

import tachiyomi.domain.category.model.Category

object CategoryMapper {
    fun mapCategory(
        id: Long,
        name: String,
        order: Long,
        flags: Long,
        parentId: Long?,
        // KMK -->
        hidden: Long,
        // KMK <--
    ): Category {
        return Category(
            id = id,
            name = name,
            order = order,
            flags = flags,
            parentId = parentId,
            // KMK -->
            hidden = hidden == 1L,
            // KMK <--
        )
    }
}
