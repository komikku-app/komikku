package exh.util

import kotlin.math.floor

fun Float.floor(): Int = floor(this).toInt()

fun Double.floor(): Int = floor(this).toInt()

fun Int.nullIfZero() = if (this == 0) null else this

fun Long.nullIfZero() = if (this == 0L) null else this
