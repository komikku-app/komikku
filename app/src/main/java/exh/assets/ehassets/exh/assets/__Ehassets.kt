package exh.assets.ehassets.exh.assets

import androidx.compose.ui.graphics.vector.ImageVector
import exh.assets.ehassets.exh.AssetsGroup
import kotlin.collections.List as ____KtList

public object EhassetsGroup

public val AssetsGroup.Ehassets: EhassetsGroup
    get() = EhassetsGroup

@Suppress("ObjectPropertyName", "ktlint:standard:backing-property-naming")
private var __AllAssets: ____KtList<ImageVector>? = null

public val EhassetsGroup.AllAssets: ____KtList<ImageVector>
    get() {
        if (__AllAssets != null) {
            return __AllAssets!!
        }
        __AllAssets = listOf()
        return __AllAssets!!
    }
