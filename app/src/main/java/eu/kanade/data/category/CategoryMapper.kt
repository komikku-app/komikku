package eu.kanade.data.category

import eu.kanade.domain.category.model.Category

val categoryMapper: (Long, String, Long, Long, List<Long>) -> Category = { id, name, order, flags, mangaOrder ->
    Category(
        id = id,
        name = name,
        order = order,
        flags = flags,
        // SY -->
        mangaOrder = mangaOrder,
        // SY <--
    )
}
