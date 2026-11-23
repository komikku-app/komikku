package app.komikku.sourcejs.repo

import app.komikku.sourcejs.JsSourceDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MangayomiIndexTest {

    /** Entries shaped like the kodjodevf / entityJY `index.json` files (contract 1.2, all 20 fields). */
    private val sample = """
        [
          {"name":"MangaDex","id":652112892,"baseUrl":"https://mangadex.org","lang":"en","typeSource":"single",
           "iconUrl":"https://raw.githubusercontent.com/kodjodevf/mangayomi-extensions/main/javascript/icon/all.mangadex.png",
           "dateFormat":"","dateFormatLocale":"","isNsfw":false,"hasCloudflare":false,
           "sourceCodeUrl":"https://raw.githubusercontent.com/entityJY/mangayomi-extensions-eJ/main/javascript/manga/src/all/mangadex.js",
           "apiUrl":"https://api.mangadex.org","version":"0.1.35","isManga":true,"itemType":0,"isFullData":false,
           "appMinVerReq":"0.5.0","additionalParams":"","sourceCodeLanguage":1,"notes":""},
          {"name":"新新漫画","id":1234567,"baseUrl":"http://www.77mh.xyz","lang":"zh","typeSource":"single",
           "iconUrl":"https://example.org/77mh.png","dateFormat":"","dateFormatLocale":"","isNsfw":true,
           "hasCloudflare":false,"sourceCodeUrl":"https://example.org/zh/77mh.js","apiUrl":"","version":"0.0.35",
           "isManga":true,"itemType":0,"isFullData":false,"appMinVerReq":"0.5.0","additionalParams":"",
           "sourceCodeLanguage":1,"notes":"This Source Just For Boruto"},
          {"name":"Dart source","id":11,"lang":"en","sourceCodeUrl":"https://example.org/a.dart","version":"1.0.0",
           "itemType":0,"sourceCodeLanguage":0,"appMinVerReq":"0.5.0"},
          {"name":"JS anime","id":12,"lang":"en","sourceCodeUrl":"https://example.org/anime.js","version":"1.0.0",
           "itemType":1,"isManga":false,"sourceCodeLanguage":1,"appMinVerReq":"0.5.0"},
          {"name":"Future source","id":13,"lang":"en","sourceCodeUrl":"https://example.org/f.js","version":"1.0.0",
           "itemType":0,"sourceCodeLanguage":1,"appMinVerReq":"0.10.0"},
          {"name":"","id":14,"sourceCodeUrl":"https://example.org/x.js","sourceCodeLanguage":1},
          {"name":"No code","id":15,"sourceCodeLanguage":1},
          {"name":"No id","sourceCodeUrl":"https://example.org/y.js","sourceCodeLanguage":1},
          "not an object",
          {"name":"Loose types","id":"16","sourceCodeUrl":"https://example.org/z.js","sourceCodeLanguage":"1",
           "isNsfw":"true","version":2,"futureField":{"nested":[1,2]},"notes":null,"lang":["en"]}
        ]
    """.trimIndent()

    @Test
    fun keepsOnlyJavaScriptMangaEntriesTheTargetAppCanRun() {
        val index = MangayomiIndex.parse(sample)
        assertEquals(listOf(652112892L, 1234567L, 16L), index.entries.map { it.id })
        assertEquals(7, index.skipped)

        val mangadex = index.entries[0]
        assertEquals("MangaDex", mangadex.name)
        assertEquals("en", mangadex.lang)
        assertEquals("https://mangadex.org", mangadex.baseUrl)
        assertEquals("https://api.mangadex.org", mangadex.apiUrl)
        assertEquals("0.1.35", mangadex.version)
        assertEquals(1, mangadex.sourceCodeLanguage)
        assertEquals(0, mangadex.itemType)
        assertTrue(mangadex.sourceCodeUrl.endsWith("/all/mangadex.js"))
        assertTrue(mangadex.iconUrl.endsWith("all.mangadex.png"))

        val zh = index.entries[1]
        assertTrue(zh.isNsfw)
        assertEquals("This Source Just For Boruto", zh.notes)
        assertEquals("http://www.77mh.xyz", zh.baseUrl)
    }

    @Test
    fun readsFieldsOfUnexpectedTypesWhereUnambiguous() {
        val loose = MangayomiIndex.parse(sample).entries.single { it.id == 16L }
        assertTrue(loose.isNsfw)
        assertEquals("2", loose.version)
        assertEquals("", loose.notes)
        // An array where a string belongs counts as missing.
        assertEquals("", loose.lang)
        // A missing `appMinVerReq` is no requirement.
        assertEquals("", loose.appMinVerReq)
    }

    @Test
    fun aMissingItemTypeFollowsIsMangaAndAMissingLanguageIsDart() {
        val index = MangayomiIndex.parse(
            """[
              {"name":"A","id":1,"sourceCodeUrl":"https://e/a.js","sourceCodeLanguage":1},
              {"name":"B","id":2,"sourceCodeUrl":"https://e/b.js","sourceCodeLanguage":1,"isManga":false},
              {"name":"C","id":3,"sourceCodeUrl":"https://e/c.js"}
            ]""",
        )
        assertEquals(listOf(1L), index.entries.map { it.id })
    }

    @Test
    fun duplicateIdsKeepTheHighestVersion() {
        val index = MangayomiIndex.parse(
            """[
              {"name":"Old","id":5,"sourceCodeUrl":"https://e/old.js","sourceCodeLanguage":1,"version":"0.0.9"},
              {"name":"New","id":5,"sourceCodeUrl":"https://e/new.js","sourceCodeLanguage":1,"version":"0.0.10"},
              {"name":"Same","id":5,"sourceCodeUrl":"https://e/same.js","sourceCodeLanguage":1,"version":"0.0.10"}
            ]""",
        )
        assertEquals("New", index.entries.single().name)
        assertEquals(2, index.skipped)
    }

    @Test
    fun otherDocumentsAreRejected() {
        assertFailsWith<MangayomiIndexException> { MangayomiIndex.parse("""{"name":"v2 store"}""") }
        assertFailsWith<MangayomiIndexException> { MangayomiIndex.parse("<html>Not found</html>") }
        assertFailsWith<MangayomiIndexException> { MangayomiIndex.parse("") }
        assertEquals(0, MangayomiIndex.parse("﻿[]").entries.size)
    }

    @Test
    fun repoInfoIsOptionalAndMayBeUnderMeta() {
        assertEquals(
            "https://example.org/ext/repo.json",
            MangayomiRepoInfo.infoUrl("https://example.org/ext/index.json?ref=main#x"),
        )
        assertEquals(
            MangayomiRepoInfo("Kodjo", "https://github.com/kodjodevf", null),
            MangayomiRepoInfo.parse("""{"name":"Kodjo","website":"https://github.com/kodjodevf"}"""),
        )
        assertEquals(
            MangayomiRepoInfo("eJ", null, "https://example.org/v2.json"),
            MangayomiRepoInfo.parse("""{"meta":{"name":" eJ ","website":"","index_v2":"https://example.org/v2.json"}}"""),
        )
        assertEquals(MangayomiRepoInfo(null, null, null), MangayomiRepoInfo.parse("""{"name":3}"""))
        assertNull(MangayomiRepoInfo.parse("[]"))
        assertNull(MangayomiRepoInfo.parse("404: Not Found"))
    }

    @Test
    fun repoInfoUrlResolvesAgainstTheIndexUrl() {
        // No path: the file is at the root, the host stays.
        assertEquals("https://example.com/repo.json", MangayomiRepoInfo.infoUrl("https://example.com"))
        assertEquals("https://example.com:8443/repo.json", MangayomiRepoInfo.infoUrl("https://example.com:8443"))
        // A query (or fragment) containing "/" is not part of the path.
        assertEquals("https://example.com/repo.json", MangayomiRepoInfo.infoUrl("https://example.com?index=a/b/c.json"))
        assertEquals("https://example.com/repo.json", MangayomiRepoInfo.infoUrl("https://example.com#a/b"))
        assertEquals("https://example.com/ext/repo.json", MangayomiRepoInfo.infoUrl("https://example.com/ext/index.json?p=x/y#z/w"))
        // A trailing slash names a directory: the file is inside it.
        assertEquals("https://example.com/ext/repo.json", MangayomiRepoInfo.infoUrl("https://example.com/ext/"))
        assertEquals("https://example.com/repo.json", MangayomiRepoInfo.infoUrl("https://example.com/"))
        assertEquals("https://example.com/repo.json", MangayomiRepoInfo.infoUrl("https://example.com/index.json"))
    }

    @Test
    fun versionsCompareLikeMangayomi() {
        fun cmp(a: String, b: String) = MangayomiVersions.compare(a, b).coerceIn(-1, 1)
        assertEquals(-1, cmp("0.0.9", "0.0.10"))
        assertEquals(1, cmp("0.2.20", "0.1.25"))
        assertEquals(0, cmp("1.0", "1.0.0"))
        assertEquals(0, cmp("v1.2.3", "1.2.3+build.7"))
        assertEquals(0, cmp("1.x.3", "1.0.3"))
        assertEquals(1, cmp("1.0.0", "1.0.0-beta"))
        assertEquals(-1, cmp("1.0.0-alpha", "1.0.0"))
        assertEquals(-1, cmp("1.0.0-2", "1.0.0-10"))
        assertEquals(-1, cmp("1.0.0-1", "1.0.0-alpha"))
        assertEquals(-1, cmp("1.0.0-alpha", "1.0.0-beta"))
        assertEquals(-1, cmp("1.0.0-alpha", "1.0.0-alpha.1"))
        assertEquals(0, cmp("", "0"))
        assertTrue(MangayomiVersions.isAtMost("0.5.0", MangayomiIndex.TARGET_APP_VERSION))
        assertTrue(MangayomiVersions.isAtMost("0.9.96", MangayomiIndex.TARGET_APP_VERSION))
        assertTrue(MangayomiVersions.isAtMost(" ", MangayomiIndex.TARGET_APP_VERSION))
        assertTrue(!MangayomiVersions.isAtMost("0.9.97", MangayomiIndex.TARGET_APP_VERSION))
    }

    @Test
    fun offersMergeByIdAndStatusesFollowVersionsAndText() {
        fun entry(id: Long, version: String, name: String = "S$id") =
            JsSourceDescriptor(id = id, name = name, version = version, sourceCodeUrl = "https://e/$id.js")
        val merged = JsSourceOffer.merge(
            listOf(
                "https://a/index.json" to listOf(entry(1, "0.1.0", "A1"), entry(2, "0.2.0", "A2")),
                "https://b/index.json" to listOf(entry(1, "0.1.1", "B1"), entry(2, "0.2.0", "B2"), entry(3, "1", "B3")),
            ),
        )
        assertEquals(listOf("B1", "A2", "B3"), merged.map { it.descriptor.name })
        assertEquals("https://b/index.json", merged[0].repoUrl)

        val offer = entry(1, "0.1.1")
        assertEquals(JsSourceStatus.UPDATE_AVAILABLE, JsSourceStatus.of("0.1.0", "aa", offer, null))
        assertEquals(JsSourceStatus.UP_TO_DATE, JsSourceStatus.of("0.1.1", "aa", offer, null))
        assertEquals(JsSourceStatus.UP_TO_DATE, JsSourceStatus.of("0.1.1", "aa", offer, "AA"))
        assertEquals(JsSourceStatus.TEXT_CHANGED, JsSourceStatus.of("0.1.1", "aa", offer, "bb"))
        assertEquals(JsSourceStatus.UP_TO_DATE, JsSourceStatus.of("0.2.0", "aa", offer, "bb"))
        assertEquals(JsSourceStatus.OBSOLETE, JsSourceStatus.of("0.1.1", "aa", null, null))
        assertEquals(JsSourceStatus.UP_TO_DATE, JsSourceStatus.of("0.1.1", "aa", null, null, listingKnown = false))
    }
}
