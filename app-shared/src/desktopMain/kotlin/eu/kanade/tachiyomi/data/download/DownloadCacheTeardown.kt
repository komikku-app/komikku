package eu.kanade.tachiyomi.data.download

/**
 * KMK: desktop teardown only (the data folder is about to be deleted, tests): closes the borrowed [DownloadCache],
 * stops its background work (the delayed index write into `cache/`) and waits for it. Idempotent. The cache's own
 * `cancelAndJoin()` is `internal` so the Android app cannot call it; this desktop-only wrapper is the one way in.
 */
suspend fun DownloadCache.stopForDesktopTeardown() = cancelAndJoin()
