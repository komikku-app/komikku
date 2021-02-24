package exh.util

import kotlin.math.floor

fun Float.floor(): Int = floor(this).toInt()

fun Double.floor(): Int = floor(this).toInt()

fun Int.nullIfZero() = takeUnless { it == 0 }

fun Long.nullIfZero() = takeUnless { it == 0L }
