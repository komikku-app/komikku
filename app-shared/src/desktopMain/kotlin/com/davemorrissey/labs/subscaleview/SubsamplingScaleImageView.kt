package com.davemorrissey.labs.subscaleview

/**
 * KMK: desktop stand-in for the Android image view library class, constants only (the values of
 * `com.github.tachiyomiorg:subsampling-scale-image-view`), for the borrowed `ReaderPreferences`.
 * The desktop reader maps `ReaderPreferences.imageScaleType()` to its own fit modes with them.
 */
abstract class SubsamplingScaleImageView private constructor() {
    companion object {
        const val SCALE_TYPE_CENTER_INSIDE = 1
        const val SCALE_TYPE_CENTER_CROP = 2
        const val SCALE_TYPE_FIT_WIDTH = 3
        const val SCALE_TYPE_FIT_HEIGHT = 4
        const val SCALE_TYPE_ORIGINAL_SIZE = 5
        const val SCALE_TYPE_SMART_FIT = 6
        const val SCALE_TYPE_CUSTOM = 7
    }
}
