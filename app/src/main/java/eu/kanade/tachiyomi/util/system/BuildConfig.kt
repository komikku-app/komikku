package eu.kanade.tachiyomi.util.system

import eu.kanade.tachiyomi.BuildConfig
import exh.syDebugVersion

val isDevFlavor: Boolean
    get() = BuildConfig.FLAVOR == "dev"

val isReleaseFlavor: Boolean
    get() = BuildConfig.BUILD_TYPE == "release" /* SY --> */ && syDebugVersion == "0" /* SY <-- */
