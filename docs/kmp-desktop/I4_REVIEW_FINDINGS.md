# I4 review findings (open)

Medium review of `5780d0e^..dff17e0` (I4 repository store and UI). **Nothing is fixed yet.** Next action: fix all
of them, re-report each finding with its outcome, then delete this file (move what stays open to `PROGRESS.md`).

## Important

1. **`app-shared/src/main/kotlin/app/komikku/shared/jssource/JsSourcesScreenModel.kt:105` — a disk error during
   install / update / uninstall crashes the app.** `busy()` only catches `JsSourceStoreException`; the store's file
   writes (`MangayomiSourceStore.install` / `uninstall`: `writeAtomically`, `downloadIcon`, `writeManifest`) let a
   raw `IOException` through. Fix: wrap the store's file failures into a `JsSourceStoreException` (new reason, e.g.
   `STORAGE_FAILED`, with its own message string) and make `busy()` robust against any `Exception`.
2. **`app/src/main/java/eu/kanade/tachiyomi/source/AndroidSourceManager.kt:143` and
   `DesktopSourceManager.kt:100` — `isInitialized` is set before the JavaScript sources are loaded.** Cold start
   with queued downloads of a JS source: `DownloadStore.restore()` gets null for the source and `clear()` wipes the
   queue; `DownloadCache` builds its map without JS sources; the reader can resolve a stub. Fix: wait for the
   store's `isLoaded` (bounded by a timeout so a broken store cannot block start-up forever) before setting
   `_isInitialized`.
3. **`MangayomiSourceStore.kt:247` — `refresh()` replaces the whole `listings` map** captured from the URL set at
   its start, dropping a repository added meanwhile and re-adding one removed meanwhile. Fix: merge, filtered by
   the current repository preference.
4. **`MangayomiSourceStore.kt:348` — a failed update leaves the old source closed.** `runtime.source()` replaces
   and closes the previous source before anything is persisted. Fix: build the new source without replacing the
   old one and swap only after the manifest is written (or restore the old one on failure); clean up the new code
   file / icon on failure.
5. **`desktopApp/src/main/kotlin/app/komikku/desktop/ui/browse/BrowseHomeScreen.kt:59` — opening desktop Browse
   refreshes every JS repository** (screen model created eagerly, `init` calls `refresh()`), and the model's store
   lookup throws when the store cannot be built. Fix: create the model only when the JS tab is selected; guard the
   lookup; check the Android screen for the same eager refresh.
6. **`MangayomiSourceStore.kt:317` — the install dialog shows the pre-redirect host.** Fix: return the final URL
   from `fetchBytes` and show its host (both hosts when they differ).

## Nits

7. **`MangayomiSourceStore.kt:178` — a source is shown as Obsolete too early** (`listingKnown` looks only at the
   repository it was installed from). Fix: known only when every added repository has entries.
8. **`MangayomiSourceStore.kt:516` — stale icon after a failed icon download on update.** Fix: delete the
   existing icon in the failure branch.
9. **`source-js/src/commonMain/kotlin/app/komikku/sourcejs/repo/MangayomiRepoInfo.kt:18` — `infoUrl` is wrong for
   an index URL without a path** (`https://example.com` → `https://repo.json`). Fix: proper URL resolution in
   common code (no JVM types), with tests for no path, query containing `/`, and trailing slash.
