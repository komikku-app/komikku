package eu.kanade.tachiyomi.util

import rx.Observable
import tachiyomi.core.common.util.lang.awaitSingle

// KMK: desktop actual, same as Android (core:common is shared).
actual suspend fun <T> Observable<T>.awaitSingle(): T = awaitSingle()
