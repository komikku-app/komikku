package mihon.core.migration.migrations

import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.tachiyomi.source.Source
import exh.source.MERGED_SOURCE_ID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.data.DatabaseHandler
import tachiyomi.data.chapter.ChapterMapper
import tachiyomi.domain.chapter.interactor.DeleteChapters
import tachiyomi.domain.chapter.interactor.UpdateChapter
import tachiyomi.domain.chapter.model.ChapterUpdate
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.interactor.GetMangaBySource
import tachiyomi.domain.manga.interactor.InsertMergedReference
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.manga.model.MangaUpdate
import tachiyomi.domain.manga.model.MergedMangaReference
import tachiyomi.domain.source.service.SourceManager

class MergedMangaRewriteMigration : Migration {
    override val version: Float = 7f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val handler = migrationContext.get<DatabaseHandler>() ?: return@withIOContext false
        val getMangaBySource = migrationContext.get<GetMangaBySource>() ?: return@withIOContext false
        val getManga = migrationContext.get<GetManga>() ?: return@withIOContext false
        val updateManga = migrationContext.get<UpdateManga>() ?: return@withIOContext false
        val insertMergedReference = migrationContext.get<InsertMergedReference>() ?: return@withIOContext false
        val sourceManager = migrationContext.get<SourceManager>() ?: return@withIOContext false
        val deleteChapters = migrationContext.get<DeleteChapters>() ?: return@withIOContext false
        val updateChapter = migrationContext.get<UpdateChapter>() ?: return@withIOContext false
        val mergedMangas = getMangaBySource.await(MERGED_SOURCE_ID)

        if (mergedMangas.isNotEmpty()) {
            val mangaConfigs = mergedMangas.mapNotNull { mergedManga ->
                readMangaConfig(mergedManga)?.let { mergedManga to it }
            }
            if (mangaConfigs.isNotEmpty()) {
                val mangaToUpdate = mutableListOf<MangaUpdate>()
                val mergedMangaReferences = mutableListOf<MergedMangaReference>()
                mangaConfigs.onEach { mergedManga ->
                    val newFirst = mergedManga.second.children.firstOrNull()?.url?.let {
                        if (getManga.await(it, MERGED_SOURCE_ID) != null) return@onEach
                        mangaToUpdate += MangaUpdate(id = mergedManga.first.id, url = it)
                        mergedManga.first.copy(url = it)
                    } ?: mergedManga.first
                    mergedMangaReferences += MergedMangaReference(
                        id = -1,
                        isInfoManga = false,
                        getChapterUpdates = false,
                        chapterSortMode = 0,
                        chapterPriority = 0,
                        downloadChapters = false,
                        mergeId = newFirst.id,
                        mergeUrl = newFirst.url,
                        mangaId = newFirst.id,
                        mangaUrl = newFirst.url,
                        mangaSourceId = MERGED_SOURCE_ID,
                    )
                    mergedManga.second.children.distinct().forEachIndexed { index, mangaSource ->
                        val load = mangaSource.load(getManga, sourceManager) ?: return@forEachIndexed
                        mergedMangaReferences += MergedMangaReference(
                            id = -1,
                            isInfoManga = index == 0,
                            getChapterUpdates = true,
                            chapterSortMode = 0,
                            chapterPriority = 0,
                            downloadChapters = true,
                            mergeId = newFirst.id,
                            mergeUrl = newFirst.url,
                            mangaId = load.manga.id,
                            mangaUrl = load.manga.url,
                            mangaSourceId = load.source.id,
                        )
                    }
                }

                updateManga.awaitAll(mangaToUpdate)
                insertMergedReference.awaitAll(mergedMangaReferences)

                val loadedMangaList = mangaConfigs
                    .map { it.second.children }
                    .flatten()
                    .mapNotNull { it.load(getManga, sourceManager) }
                    .distinct()
                val chapters =
                    handler.awaitList {
                        ehQueries.getChaptersByMangaIds(
                            mergedMangas.map { it.id },
                            ChapterMapper::mapChapter,
                        )
                    }

                val mergedMangaChapters =
                    handler.awaitList {
                        ehQueries.getChaptersByMangaIds(
                            loadedMangaList.map { it.manga.id },
                            ChapterMapper::mapChapter,
                        )
                    }

                val mergedMangaChaptersMatched = mergedMangaChapters.mapNotNull { chapter ->
                    loadedMangaList.firstOrNull {
                        it.manga.id == chapter.id
                    }?.let { it to chapter }
                }
                val parsedChapters = chapters.filter {
                    it.read || it.lastPageRead != 0L
                }.mapNotNull { chapter -> readUrlConfig(chapter.url)?.let { chapter to it } }
                val chaptersToUpdate = mutableListOf<ChapterUpdate>()
                parsedChapters.forEach { parsedChapter ->
                    mergedMangaChaptersMatched.firstOrNull {
                        it.second.url == parsedChapter.second.url &&
                            it.first.source.id == parsedChapter.second.source &&
                            it.first.manga.url == parsedChapter.second.mangaUrl
                    }?.let {
                        chaptersToUpdate += ChapterUpdate(
                            it.second.id,
                            read = parsedChapter.first.read,
                            lastPageRead = parsedChapter.first.lastPageRead,
                        )
                    }
                }

                deleteChapters.await(mergedMangaChapters.map { it.id })
                updateChapter.awaitAll(chaptersToUpdate)
            }
        }
        return@withIOContext true
    }

    @Serializable
    private data class UrlConfig(
        @SerialName("s")
        val source: Long,
        @SerialName("u")
        val url: String,
        @SerialName("m")
        val mangaUrl: String,
    )

    @Serializable
    private data class MangaConfig(
        @SerialName("c")
        val children: List<MangaSource>,
    ) {
        companion object {
            fun readFromUrl(url: String): MangaConfig? {
                return try {
                    Json.decodeFromString(url)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private fun readMangaConfig(manga: Manga): MangaConfig? {
        return MangaConfig.readFromUrl(manga.url)
    }

    @Serializable
    private data class MangaSource(
        @SerialName("s")
        val source: Long,
        @SerialName("u")
        val url: String,
    ) {
        suspend fun load(getManga: GetManga, sourceManager: SourceManager): LoadedMangaSource? {
            val manga = getManga.await(url, source) ?: return null
            val source = sourceManager.getOrStub(source)
            return LoadedMangaSource(source, manga)
        }
    }

    private fun readUrlConfig(url: String): UrlConfig? {
        return try {
            Json.decodeFromString(url)
        } catch (e: Exception) {
            null
        }
    }

    private data class LoadedMangaSource(val source: Source, val manga: Manga)
}
