/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package exh.util

import kotlin.time.DurationUnit
import kotlin.time.toDuration

val Int.nanoseconds get() = toDuration(DurationUnit.NANOSECONDS)
val Long.nanoseconds get() = toDuration(DurationUnit.NANOSECONDS)
val Double.nanoseconds get() = toDuration(DurationUnit.NANOSECONDS)
val Int.microseconds get() = toDuration(DurationUnit.MICROSECONDS)
val Long.microseconds get() = toDuration(DurationUnit.MICROSECONDS)
val Double.microseconds get() = toDuration(DurationUnit.MICROSECONDS)
val Int.milliseconds get() = toDuration(DurationUnit.MILLISECONDS)
val Long.milliseconds get() = toDuration(DurationUnit.MILLISECONDS)
val Double.milliseconds get() = toDuration(DurationUnit.MILLISECONDS)
val Int.seconds get() = toDuration(DurationUnit.SECONDS)
val Long.seconds get() = toDuration(DurationUnit.SECONDS)
val Double.seconds get() = toDuration(DurationUnit.SECONDS)
val Int.minutes get() = toDuration(DurationUnit.MINUTES)
val Long.minutes get() = toDuration(DurationUnit.MINUTES)
val Double.minutes get() = toDuration(DurationUnit.MINUTES)
val Int.hours get() = toDuration(DurationUnit.HOURS)
val Long.hours get() = toDuration(DurationUnit.HOURS)
val Double.hours get() = toDuration(DurationUnit.HOURS)
val Int.days get() = toDuration(DurationUnit.DAYS)
val Long.days get() = toDuration(DurationUnit.DAYS)
val Double.days get() = toDuration(DurationUnit.DAYS)
