package exh.assets.ehassets

import androidx.compose.ui.graphics.vector.ImageVector
import exh.assets.EhAssets
import exh.assets.ehassets.exh.AllAssets
import exh.assets.ehassets.exh.Assets
import kotlin.collections.List as ____KtList

public object ExhGroup

public val EhAssets.Exh: ExhGroup
    get() = ExhGroup

@Suppress("ObjectPropertyName", "ktlint:standard:backing-property-naming")
private var __AllAssets: ____KtList<ImageVector>? = null

public val ExhGroup.AllAssets: ____KtList<ImageVector>
    get() {
        if (__AllAssets != null) {
            return __AllAssets!!
        }
        __AllAssets = Assets.AllAssets + listOf()
        return __AllAssets!!
    }
