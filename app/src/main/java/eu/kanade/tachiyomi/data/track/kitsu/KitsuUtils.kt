package eu.kanade.tachiyomi.data.track.kitsu

import eu.kanade.tachiyomi.data.database.models.Track
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.text.DecimalFormat

fun Track.toApiStatus() = when (status) {
    Kitsu.READING -> "current"
    Kitsu.COMPLETED -> "completed"
    Kitsu.ON_HOLD -> "on_hold"
    Kitsu.DROPPED -> "dropped"
    Kitsu.PLAN_TO_READ -> "planned"
    else -> throw Exception("Unknown status")
}

fun Track.toApiScore(): String? {
    return if (score > 0) (score * 2).toInt().toString() else null
}

// KMK -->
private const val AWFUL = "Awful"
private const val MEH = "Meh"
private const val GOOD = "Good"
private const val GREAT = "Great"

// DecimalFormat is not thread safe, so build one where it is used rather than sharing an instance.
private fun formatScore(score: Number): String = DecimalFormat("0.#").format(score)

/**
 * A track's score is Kitsu's `ratingTwenty` halved, so all three rating systems fit in it and only
 * the presentation differs.
 *
 * The three functions below must stay in lockstep: [kitsuDisplayScore] has to return an element of
 * [kitsuScoreList], and [kitsuIndexToScore] has to be the inverse of a position in that list.
 * `BaseTracker.setRemoteScore` resolves a picked string with `getScoreList().indexOf(...)`, so a
 * mismatch yields -1, which maps to a score of 0 and silently wipes the rating on the user's
 * account.
 */
fun kitsuScoreList(ratingSystem: String): ImmutableList<String> = when (ratingSystem) {
    // ratingTwenty 2, 8, 14, 20
    Kitsu.SIMPLE -> persistentListOf("-", AWFUL, MEH, GOOD, GREAT)
    // ratingTwenty in increments of 2, shown as 5 stars in 0.5 steps
    Kitsu.REGULAR -> (listOf("0") + IntRange(1, 10).map { "${formatScore(it / 2f)} ★" })
        .toImmutableList()
    // ratingTwenty in increments of 1, shown as 1-10 in 0.5 steps
    else -> (listOf("0") + IntRange(2, 20).map { formatScore(it / 2f) }).toImmutableList()
}

fun kitsuIndexToScore(ratingSystem: String, index: Int): Double = when {
    index <= 0 -> 0.0
    ratingSystem == Kitsu.SIMPLE -> when (index) {
        1 -> 1.0
        2 -> 4.0
        3 -> 7.0
        else -> 10.0
    }
    ratingSystem == Kitsu.REGULAR -> index.toDouble()
    else -> (index + 1) / 2.0
}

fun kitsuDisplayScore(ratingSystem: String, score: Double): String = when (ratingSystem) {
    Kitsu.SIMPLE -> when {
        score <= 0.0 -> "-"
        score < 3.0 -> AWFUL
        score < 5.0 -> MEH
        score < 8.0 -> GOOD
        else -> GREAT
    }
    Kitsu.REGULAR -> if (score <= 0.0) "0" else "${formatScore(score / 2)} ★"
    else -> formatScore(score)
}
// KMK <--
