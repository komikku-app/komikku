package eu.kanade.tachiyomi.data.track

import android.content.Context
import eu.kanade.tachiyomi.data.track.anilist.Anilist
import eu.kanade.tachiyomi.data.track.bangumi.Bangumi
import eu.kanade.tachiyomi.data.track.kitsu.Kitsu
import eu.kanade.tachiyomi.data.track.komga.Komga
import eu.kanade.tachiyomi.data.track.mdlist.MdList
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.shikimori.Shikimori

class TrackManager(context: Context) {

    companion object {
        const val MYANIMELIST = 1
        const val ANILIST = 2
        const val KITSU = 3
        const val SHIKIMORI = 4
        const val BANGUMI = 5
        const val KOMGA = 6

        // SY --> Mangadex from Neko
        const val MDLIST = 60
        // SY <--

        // SY -->
        const val READING = 1
        const val REREADING = 2
        const val PLANTOREAD = 3
        const val PAUSED = 4
        const val COMPLETED = 5
        const val DROPPED = 6
        const val OTHER = 7
        // SY <--
    }

    val mdList = MdList(context, MDLIST)

    val myAnimeList = MyAnimeList(context, MYANIMELIST)

    val aniList = Anilist(context, ANILIST)

    val kitsu = Kitsu(context, KITSU)

    val shikimori = Shikimori(context, SHIKIMORI)

    val bangumi = Bangumi(context, BANGUMI)

    val komga = Komga(context, KOMGA)

    val services = listOf(mdList, myAnimeList, aniList, kitsu, shikimori, bangumi, komga)

    fun getService(id: Int) = services.find { it.id == id }

    fun hasLoggedServices() = services.any { it.isLogged }

    // SY -->
    fun mapTrackingOrder(status: String, context: Context): Int {
        with(context) {
            return when (status) {
                getString(eu.kanade.tachiyomi.R.string.reading), getString(eu.kanade.tachiyomi.R.string.currently_reading) -> READING
                getString(eu.kanade.tachiyomi.R.string.repeating) -> REREADING
                getString(eu.kanade.tachiyomi.R.string.plan_to_read), getString(eu.kanade.tachiyomi.R.string.want_to_read) -> PLANTOREAD
                getString(eu.kanade.tachiyomi.R.string.on_hold), getString(eu.kanade.tachiyomi.R.string.paused) -> PAUSED
                getString(eu.kanade.tachiyomi.R.string.completed) -> COMPLETED
                getString(eu.kanade.tachiyomi.R.string.dropped) -> DROPPED
                else -> OTHER
            }
        }
    }
    // SY <--
}
