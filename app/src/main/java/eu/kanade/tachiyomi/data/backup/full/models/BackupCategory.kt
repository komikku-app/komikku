package eu.kanade.tachiyomi.data.backup.full.models

import eu.kanade.tachiyomi.data.database.models.CategoryImpl
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class BackupCategory(
    @ProtoNumber(1) var name: String,
    @ProtoNumber(2) var order: Long = 0,
    // @ProtoNumber(3) val updateInterval: Int = 0, 1.x value not used in 0.x
    // Bump by 100 to specify this is a 0.x value
    @ProtoNumber(100) var flags: Long = 0,
    // SY specific values
    @ProtoNumber(600) var mangaOrder: List<Long> = emptyList(),
) {
    fun getCategoryImpl(): CategoryImpl {
        return CategoryImpl().apply {
            name = this@BackupCategory.name
            flags = this@BackupCategory.flags.toInt()
            order = this@BackupCategory.order.toInt()
            mangaOrder = this@BackupCategory.mangaOrder
        }
    }
}

val backupCategoryMapper = { _: Long, name: String, order: Long, flags: Long, mangaOrder: List<Long> ->
    BackupCategory(
        name = name,
        order = order,
        flags = flags,
        mangaOrder = mangaOrder,
    )
}
