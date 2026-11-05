package mihon.domain.migration.usecases

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.DownloadCache
import eu.kanade.tachiyomi.data.download.DownloadProvider
import eu.kanade.tachiyomi.source.Source
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.storage.extension
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.chapter.repository.ChapterRepository
import tachiyomi.domain.download.model.ChapterDownload
import tachiyomi.domain.download.repository.ChapterDownloadRepository
import tachiyomi.domain.manga.model.Manga

/**
 * Copies downloaded chapter directories or `.cbz` files into the target source's manga folder.
 *
 * Catalog matches land under the target chapter's canonical name and `chapter_downloads` points at
 * that chapter id. Unmatched downloads become `orphaned://` rows on the target (same idea as
 * reindex orphans) so files are not left behind on the old entry.
 *
 * The old file is deleted only after the copy and the registry upsert both succeed. A destination
 * that already exists is left untouched.
 */
class MoveMigratedDownloads(
    private val downloadProvider: DownloadProvider,
    private val chapterDownloadRepository: ChapterDownloadRepository,
    private val downloadCache: DownloadCache,
    private val chapterRepository: ChapterRepository,
) {
    data class Counts(
        val moved: Int,
        val skipped: Int,
    )

    suspend fun move(
        plan: PlanDownloadMigration.Plan,
        current: Manga,
        target: Manga,
        currentSource: Source,
        targetSource: Source,
    ): Counts {
        var moved = 0
        var skipped = 0
        for (move in plan.moves) {
            val didMove = try {
                moveOntoCatalogChapter(move, current, target, currentSource, targetSource)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) {
                    "Failed to move download for chapter ${move.oldChapter.id}"
                }
                false
            }
            if (didMove) moved += 1 else skipped += 1
        }
        for (oldChapter in plan.orphanOldChapters) {
            val didMove = try {
                moveAsOrphan(oldChapter, current, target, currentSource, targetSource)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) {
                    "Failed to move unmatched download for chapter ${oldChapter.id}"
                }
                false
            }
            if (didMove) moved += 1 else skipped += 1
        }
        return Counts(moved, skipped)
    }

    private suspend fun moveOntoCatalogChapter(
        move: PlanDownloadMigration.Move,
        current: Manga,
        target: Manga,
        currentSource: Source,
        targetSource: Source,
    ): Boolean {
        val targetChapter = move.targetChapter
        if (targetChapter.id <= 0L) return false

        val sourceFile = findOldChapterFile(currentSource, current, move.oldChapter) ?: return false
        val targetDir = downloadProvider.getMangaDir(target.ogTitle, targetSource).getOrNull() ?: return false

        val sourceName = sourceFile.name ?: return false
        val baseName = downloadProvider.getChapterDirName(
            targetChapter.name,
            targetChapter.scanlator,
            targetChapter.url,
        )
        val destinationName = if (sourceFile.isFile && sourceFile.extension == "cbz") {
            "$baseName.cbz"
        } else {
            baseName
        }
        if (targetDir.findFile(destinationName) != null) return false
        if (!copyEntry(sourceFile, targetDir, destinationName)) return false

        return finishMovedDownload(
            sourceFile = sourceFile,
            sourceName = sourceName,
            destinationName = destinationName,
            targetDir = targetDir,
            targetChapterId = targetChapter.id,
            oldChapter = move.oldChapter,
            current = current,
            target = target,
            targetSource = targetSource,
        )
    }

    private suspend fun moveAsOrphan(
        oldChapter: Chapter,
        current: Manga,
        target: Manga,
        currentSource: Source,
        targetSource: Source,
    ): Boolean {
        val sourceFile = findOldChapterFile(currentSource, current, oldChapter) ?: return false
        val targetDir = downloadProvider.getMangaDir(target.ogTitle, targetSource).getOrNull() ?: return false
        val sourceName = sourceFile.name ?: return false
        if (targetDir.findFile(sourceName) != null) return false

        val chapterName = if (sourceFile.isFile && sourceFile.extension == "cbz") {
            sourceName.dropLast(4)
        } else {
            sourceName
        }
        if (!copyEntry(sourceFile, targetDir, sourceName)) return false

        val inserted = try {
            chapterRepository.addAll(
                listOf(
                    Chapter.create().copy(
                        mangaId = target.id,
                        url = "orphaned://$chapterName",
                        name = chapterName,
                        chapterNumber = oldChapter.chapterNumber,
                        sourceOrder = -1L,
                        read = oldChapter.read,
                        bookmark = oldChapter.bookmark,
                        lastPageRead = oldChapter.lastPageRead,
                        dateFetch = oldChapter.dateFetch,
                        dateUpload = oldChapter.dateUpload,
                        scanlator = oldChapter.scanlator,
                    ),
                ),
            ).singleOrNull()
        } catch (e: CancellationException) {
            targetDir.findFile(sourceName)?.delete()
            throw e
        } catch (e: Throwable) {
            targetDir.findFile(sourceName)?.delete()
            throw e
        }

        if (inserted == null || inserted.id <= 0L) {
            targetDir.findFile(sourceName)?.delete()
            return false
        }

        val finished = try {
            finishMovedDownload(
                sourceFile = sourceFile,
                sourceName = sourceName,
                destinationName = sourceName,
                targetDir = targetDir,
                targetChapterId = inserted.id,
                oldChapter = oldChapter,
                current = current,
                target = target,
                targetSource = targetSource,
            )
        } catch (e: CancellationException) {
            chapterRepository.removeChaptersWithIds(listOf(inserted.id))
            throw e
        } catch (e: Throwable) {
            chapterRepository.removeChaptersWithIds(listOf(inserted.id))
            throw e
        }

        if (!finished) {
            chapterRepository.removeChaptersWithIds(listOf(inserted.id))
        }
        return finished
    }

    private suspend fun finishMovedDownload(
        sourceFile: UniFile,
        sourceName: String,
        destinationName: String,
        targetDir: UniFile,
        targetChapterId: Long,
        oldChapter: Chapter,
        current: Manga,
        target: Manga,
        targetSource: Source,
    ): Boolean {
        val relativePath = downloadProvider.getRelativeChapterPath(
            targetSource,
            target.ogTitle,
            destinationName,
        )
        try {
            chapterDownloadRepository.upsert(
                ChapterDownload(
                    chapterId = targetChapterId,
                    relativePath = relativePath,
                    linkedAt = System.currentTimeMillis(),
                ),
            )
            downloadCache.addChapter(destinationName, targetDir, target)
            downloadCache.setRegistryEntry(targetChapterId, relativePath)
        } catch (e: Throwable) {
            targetDir.findFile(destinationName)?.delete()
            throw e
        }

        if (!sourceFile.delete()) {
            logcat(LogPriority.ERROR) {
                "Copied download but failed to delete the old file for chapter ${oldChapter.id}"
            }
        }
        downloadCache.removeChapter(oldChapter, current)
        downloadCache.removeFolders(listOf(sourceName), current)
        if (oldChapter.id > 0L) {
            downloadCache.removeRegistryEntry(oldChapter.id)
            chapterDownloadRepository.deleteByChapterId(oldChapter.id)
        }
        return true
    }

    private fun findOldChapterFile(source: Source, manga: Manga, chapter: Chapter): UniFile? {
        val mangaDir = downloadProvider.findMangaDir(manga.ogTitle, source) ?: return null
        if (chapter.url.startsWith("orphaned://", ignoreCase = true)) {
            return mangaDir.findFile(chapter.name) ?: mangaDir.findFile("${chapter.name}.cbz")
        }
        return downloadProvider.getValidChapterDirNames(chapter.name, chapter.scanlator, chapter.url)
            .asSequence()
            .mapNotNull { mangaDir.findFile(it) }
            .firstOrNull()
    }

    private fun copyEntry(source: UniFile, destinationDir: UniFile, destinationName: String): Boolean {
        return try {
            if (source.isDirectory) {
                val created = destinationDir.createDirectory(destinationName) ?: return false
                try {
                    val copied = source.listFiles().orEmpty().all { nested ->
                        val nestedName = nested.name ?: return@all false
                        copyEntry(nested, created, nestedName)
                    }
                    if (!copied) {
                        created.delete()
                    }
                    copied
                } catch (e: CancellationException) {
                    created.delete()
                    throw e
                } catch (_: Throwable) {
                    created.delete()
                    false
                }
            } else {
                val destinationFile = destinationDir.createFile(destinationName) ?: return false
                try {
                    source.openInputStream().use { input ->
                        destinationFile.openOutputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    true
                } catch (e: CancellationException) {
                    destinationFile.delete()
                    throw e
                } catch (_: Throwable) {
                    destinationFile.delete()
                    false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }
    }
}
