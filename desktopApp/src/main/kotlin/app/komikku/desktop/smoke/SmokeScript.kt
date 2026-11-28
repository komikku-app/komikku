package app.komikku.desktop.smoke

import app.komikku.desktop.security.DesktopAppLock
import app.komikku.desktop.ui.browse.BrowseHomeScreen
import app.komikku.desktop.ui.browse.CatalogueScreen
import app.komikku.desktop.ui.manga.MangaDetailsScreen
import app.komikku.desktop.ui.more.MoreHomeScreen
import app.komikku.desktop.ui.more.SettingsCategory
import app.komikku.desktop.ui.more.SettingsCategoryScreen
import app.komikku.desktop.ui.reader.ReaderLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.source.local.LocalSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File

/**
 * One step of the smoke script: [action] does what a user would, [until] reads the app's state (navigators, database,
 * reader, lock, text on screen) until the step's result shows, at most [timeoutMillis]; [condition] says in the report
 * what was waited for. A condition that reads the screen does so first: in the Compose test (`SmokeScriptTest`) reading
 * it is what lets the frames advance.
 */
internal class SmokeStep(
    val name: String,
    val condition: String,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val action: suspend SmokeScript.() -> Unit,
    val until: suspend SmokeScript.() -> Boolean,
    /** What [until] last saw, for the report of a step that times out. */
    val state: (suspend SmokeScript.() -> String)? = null,
) {
    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 20_000L
    }
}

/**
 * The fixed script of the UI smoke mode (UI_SMOKE_DESIGN.md section 2): every destination, a settings page, the
 * fixture through the local source's catalogue into its details and the reader, a page turn by keyboard, the app lock
 * with its PIN typed, and back. Run by [run] with a screenshot after each step.
 */
internal class SmokeScript(val driver: SmokeDriver) {

    private val navigators get() = driver.navigators

    val steps: List<SmokeStep> = listOf(
        tab("01-library", 0),
        tab("02-updates", 1),
        tab("03-history", 2),
        tab("04-browse", 3),
        tab("05-more", 4),
        SmokeStep(
            name = "06-settings",
            condition = "Settings → Appearance on top of More",
            action = { driver.onUi { navigators.push(MoreHomeScreen, SettingsCategoryScreen(SettingsCategory.Appearance)) } },
            until = { driver.onUi { navigators.top(MoreHomeScreen)?.key } == "settings-${SettingsCategory.Appearance.name}" },
        ),
        SmokeStep(
            name = "07-catalogue",
            condition = "local source lists \"${SmokeFixture.TITLE}\"",
            action = {
                selectTab(3)
                driver.onUi { navigators.push(BrowseHomeScreen, CatalogueScreen(LocalSource.ID)) }
            },
            until = { SmokeFixture.TITLE in driver.texts() && fixtureManga() != null },
            state = { "shown: ${SmokeFixture.TITLE in driver.texts()}, stored: ${fixtureManga() != null}" },
        ),
        SmokeStep(
            name = "08-details",
            condition = "details shown, ${SmokeFixture.CHAPTERS.size} chapters stored",
            action = {
                val manga = checkNotNull(fixtureManga()) { "The fixture is not in the database" }
                driver.onUi { navigators.push(BrowseHomeScreen, MangaDetailsScreen(manga.id)) }
            },
            until = {
                driver.onUi { navigators.top(BrowseHomeScreen) } is MangaDetailsScreen &&
                    chapters().map { it.name }.toSet() == SmokeFixture.CHAPTERS.toSet()
            },
        ),
        SmokeStep(
            name = "09-reader",
            condition = "reader shows \"1 / ${SmokeFixture.PAGES}\"",
            action = { Injekt.get<ReaderLauncher>().open(firstChapter().mangaId, firstChapter().id) },
            until = { "1 / ${SmokeFixture.PAGES}" in driver.texts() },
        ),
        SmokeStep(
            name = "10-page-2",
            condition = "Page Down: \"2 / ${SmokeFixture.PAGES}\", last page read 1 stored",
            action = { driver.press(SmokeKey.PageDown) },
            until = { "2 / ${SmokeFixture.PAGES}" in driver.texts() && firstChapter().lastPageRead == 1L },
            state = { "last page read: ${firstChapter().lastPageRead}" },
        ),
        SmokeStep(
            name = "11-locked",
            condition = "PIN set, app locked",
            action = {
                val lock = Injekt.get<DesktopAppLock>()
                lock.enable(PIN)
                lock.lockNow()
            },
            until = { Injekt.get<DesktopAppLock>().locked.value },
        ),
        SmokeStep(
            name = "12-unlocked",
            condition = "PIN typed + Enter: app unlocked",
            action = {
                driver.type(PIN)
                driver.press(SmokeKey.Enter)
            },
            until = { !Injekt.get<DesktopAppLock>().locked.value },
        ),
        SmokeStep(
            name = "13-close",
            condition = "Escape: reader closed, details shown",
            action = { driver.press(SmokeKey.Escape) },
            until = {
                "2 / ${SmokeFixture.PAGES}" !in driver.texts() && Injekt.get<ReaderLauncher>().request.value == null
            },
            state = { "reader request: ${Injekt.get<ReaderLauncher>().request.value}" },
        ),
    )

    private fun tab(name: String, index: Int) = SmokeStep(
        name = name,
        condition = "destination ${index + 1} of the rail shown",
        action = { driver.onUi { navigators.selectTab(index) } },
        until = { driver.onUi { navigators.currentTab() } == index },
    )

    /** Selects a rail destination and lets it compose, so its stack's navigator is the registered one. */
    private suspend fun selectTab(index: Int) {
        driver.onUi { navigators.selectTab(index) }
        driver.awaitIdle(SETTLE_MILLIS)
    }

    private suspend fun fixtureManga(): Manga? = Injekt.get<GetManga>().await(SmokeFixture.TITLE, LocalSource.ID)

    private suspend fun chapters(): List<Chapter> {
        val manga = fixtureManga() ?: return emptyList()
        return Injekt.get<ChapterRepository>().getChapterByMangaId(manga.id)
    }

    private suspend fun firstChapter(): Chapter =
        chapters().single { it.name == SmokeFixture.CHAPTERS.first() }

    /**
     * Runs the steps in order into [report], a screenshot `NN-name.png` in [folder] after each; stops at the first
     * failure (screenshot `NN-name-failed.png`). Returns the failure with its stack trace, or `null` when all passed.
     */
    suspend fun run(report: SmokeReport, folder: File, steps: List<SmokeStep> = this.steps): String? {
        for (step in steps) {
            val started = System.nanoTime()
            fun elapsed() = (System.nanoTime() - started) / 1_000_000
            var lastError: Throwable? = null
            try {
                withTimeout(step.timeoutMillis + ACTION_GRACE_MILLIS) { step.action(this@SmokeScript) }
                val deadline = started + step.timeoutMillis * 1_000_000
                while (true) {
                    val met = try {
                        step.until(this)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastError = e
                        false
                    }
                    if (met) break
                    if (System.nanoTime() > deadline) {
                        val seen = runCatching { step.state?.invoke(this) }.getOrNull()?.let { "; $it" }.orEmpty()
                        val texts = runCatching { driver.texts().take(MAX_REPORTED_TEXTS) }.getOrDefault(emptyList())
                        throw StepFailure(
                            "not met within ${step.timeoutMillis} ms: ${step.condition}$seen; on screen: $texts",
                            lastError,
                        )
                    }
                    delay(POLL_MILLIS)
                }
                val waited = elapsed()
                val idle = driver.awaitIdle(SETTLE_MILLIS)
                val file = File(folder, "${step.name}.png")
                val shot = driver.screenshot(file)
                shot.problem()?.let { throw StepFailure(it, null) }
                val detail = buildList {
                    add(shot.toString())
                    if (!idle) add("UI still busy after $SETTLE_MILLIS ms")
                }.joinToString("; ")
                report.step(SmokeReport.Step(step.name, step.condition, SmokeReport.Status.PASS, waited, file.name, detail))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val failed = File(folder, "${step.name}-failed.png")
                val shot = runCatching { driver.screenshot(failed) }.getOrNull()
                report.step(
                    SmokeReport.Step(
                        step.name,
                        step.condition,
                        SmokeReport.Status.FAIL,
                        elapsed(),
                        failed.name.takeIf { shot != null },
                        e.message ?: e.toString(),
                    ),
                )
                return "${step.name}: ${e.stackTraceToString()}"
            }
        }
        return null
    }

    private class StepFailure(message: String, cause: Throwable?) : Exception(message, cause)

    companion object {
        const val PIN = "2468"
        private const val POLL_MILLIS = 50L
        private const val SETTLE_MILLIS = 2_000L
        private const val ACTION_GRACE_MILLIS = 10_000L
        private const val MAX_REPORTED_TEXTS = 40
    }
}
