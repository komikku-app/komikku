package eu.kanade.tachiyomi.data.backup.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import tachiyomi.domain.category.model.Category

@Serializable
class BackupCategory(
    @ProtoNumber(1) var name: String,
    @ProtoNumber(2) var order: Long = 0,
    @ProtoNumber(3) var id: Long = 0,
    // @ProtoNumber(3) val updateInterval: Int = 0, 1.x value not used in 0.x
    @ProtoNumber(100) var flags: Long = 0,
    // KMK -->
    @ProtoNumber(900) var hidden: Boolean = false,
    @ProtoNumber(901) var parentId: Long? = null,
    // KMK <--
    // SY specific values
    /*@ProtoNumber(600) var mangaOrder: List<Long> = emptyList(),*/
) {
    fun toCategory(id: Long, parentId: Long?) = Category(
        id = id,
        name = this@BackupCategory.name,
        flags = this@BackupCategory.flags,
        order = this@BackupCategory.order,
        // KMK -->
        hidden = this@BackupCategory.hidden,
        parentId = parentId,
        // KMK <--
        /*mangaOrder = this@BackupCategory.mangaOrder*/
    )

    companion object {
        /** Distinguishes an explicit root from an absent field in legacy backups. */
        const val ROOT_PARENT_ID = 0L
    }
}

val backupCategoryMapper = { category: Category ->
    BackupCategory(
        id = category.id,
        name = category.name,
        order = category.order,
        flags = category.flags,
        // KMK -->
        hidden = category.hidden,
        parentId = category.parentId ?: BackupCategory.ROOT_PARENT_ID,
        // KMK <--
    )
}
