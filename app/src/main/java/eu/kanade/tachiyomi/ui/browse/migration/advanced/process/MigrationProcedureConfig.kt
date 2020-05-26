package eu.kanade.tachiyomi.ui.browse.migration.advanced.process

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class MigrationProcedureConfig(
    var mangaIds: List<Long>,
    val extraSearchParams: String?
) : Parcelable
