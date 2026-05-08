# Port Yamtrack tracker from komikku to Suwayomi-Server

## Context

The Yamtrack tracker service was added to komikku (Android app) in this session: login via host URL + API token, search, bind, update, refresh, delete, reading dates, 0–10 scores, and status mapping to Yamtrack's API. The user wants the same capability in [Suwayomi-Server](https://github.com/Suwayomi/Suwayomi-Server) — a headless manga server that shares tracker architecture lineage with Tachiyomi but is a separate codebase.

Suwayomi-Server has no local checkout in this workspace. Suwayomi's tracker layer lives under `server/src/main/kotlin/suwayomi/tachidesk/manga/impl/track/tracker/`, is registered in `TrackerManager.kt`, and each tracker has its own sub-package. The most recent analogous PR is **#1839 (Shikimori)**, which is the closest structural template.

## Goal

Open a PR against `Suwayomi/Suwayomi-Server` (master branch) that adds Yamtrack as a new tracker with feature parity to the komikku implementation, following the file/package conventions used by the existing Shikimori, Bangumi, and Kitsu trackers.

## Source references (komikku, already in this repo)

- `app/src/main/java/eu/kanade/tachiyomi/data/track/yamtrack/Yamtrack.kt` — tracker class, status mapping, URL encoding, ISO date helpers
- `app/src/main/java/eu/kanade/tachiyomi/data/track/yamtrack/YamtrackApi.kt` — REST client (search/get/add/update/delete)
- `app/src/main/java/eu/kanade/tachiyomi/data/track/yamtrack/YamtrackInterceptor.kt` — Bearer-token auth interceptor
- `app/src/main/java/eu/kanade/tachiyomi/data/track/yamtrack/dto/YTMedia.kt` — DTOs, `copyToTrack`, `resolveTotalChapters`
- `app/src/main/res/drawable/brand_yamtrack.png` — official logo (512×512 PNG)

## Target files (Suwayomi-Server)

**New files**

- `server/src/main/kotlin/suwayomi/tachidesk/manga/impl/track/tracker/yamtrack/Yamtrack.kt`
- `server/src/main/kotlin/suwayomi/tachidesk/manga/impl/track/tracker/yamtrack/YamtrackApi.kt`
- `server/src/main/kotlin/suwayomi/tachidesk/manga/impl/track/tracker/yamtrack/YamtrackInterceptor.kt`
- `server/src/main/kotlin/suwayomi/tachidesk/manga/impl/track/tracker/yamtrack/dto/YTMedia.kt`
- `server/src/main/resources/static/tracker/yamtrack.png` (reuse the PNG from komikku)

**Modified files**

- `server/src/main/kotlin/suwayomi/tachidesk/manga/impl/track/tracker/TrackerManager.kt` — add `const val YAMTRACK = 10L` (next free ID after existing 1–9), instantiate `Yamtrack(YAMTRACK)`, append to `services` list.

## Porting adjustments (komikku → Suwayomi)

These are the deltas to apply while copying the komikku files, driven by known API differences between the two tracker bases:

1. **Package & imports**
   - `package eu.kanade.tachiyomi.data.track.yamtrack` → `package suwayomi.tachidesk.manga.impl.track.tracker.yamtrack`
   - Base class: `BaseTracker(id, "Yamtrack")` → `Tracker(id, "Yamtrack")` (Suwayomi has no `BaseTracker`; `Tracker` is the abstract superclass).
   - `DeletableTracker` exists in both — same name, different package.
   - Drop `dev.icerock.moko.resources.StringResource` and `tachiyomi.i18n.MR` — Suwayomi trackers return plain `String` for status labels (mirror Shikimori's pattern).

2. **Track model**
   - Replace `eu.kanade.tachiyomi.data.database.models.Track` and `tachiyomi.domain.track.model.Track` with Suwayomi's internal `Track` model. **Verification step** (see below): read Shikimori.kt / KitsuApi.kt to confirm the exact import and field names (`last_chapter_read`, `total_chapters`, `score`, `status`, `started_reading_date`, `finished_reading_date`, `tracking_url`, `remote_id`, `title`).
   - If Suwayomi's `Track` lacks `started_reading_date` / `finished_reading_date`, gate those fields behind the same check Kitsu uses, or drop them from the first PR and note as a follow-up.

3. **Logo**
   - komikku: `R.drawable.brand_yamtrack` returned from `getLogo()`.
   - Suwayomi: drop `getLogo()` override; put `yamtrack.png` in `resources/static/tracker/` (Shikimori, Kitsu, Bangumi all do this). Suwayomi resolves the asset by tracker name.

4. **`supportsReadingDates`**
   - If the Suwayomi `Tracker` base class exposes this flag, override to `true`. Otherwise omit — the date fields are still written to the server via `YamtrackApi`, and the client UI is separate (Suwayomi-WebUI).

5. **Status constants & `getStatus()`**
   - Keep the `PLANNING/READING/PAUSED/COMPLETED/DROPPED` Long constants and the `API_STATUS_*` int mapping unchanged.
   - Return `String` (not `StringResource?`) from `getStatus()` — e.g. `"Plan to Read"`, `"Reading"`, `"Paused"`, `"Completed"`, `"Dropped"` — matching Shikimori's approach.

6. **Networking**
   - komikku gets `client: OkHttpClient` from `BaseTracker`; Suwayomi's `Tracker` also exposes an `OkHttpClient` via its network injection. Keep `YamtrackApi(yamtrack, client, interceptor)` the same shape.
   - `eu.kanade.tachiyomi.network.{GET,POST,PATCH,DELETE,awaitSuccess,jsonMime,parseAs}` — verify each import resolves in Suwayomi's source tree. If a helper is missing (e.g. `parseAs`), swap for the equivalent Suwayomi utility or inline `json.decodeFromString(response.body!!.string())`.

7. **Credential storage**
   - komikku uses `saveCredentials(username=baseUrl, password=token)` via `BaseTracker`. Suwayomi's `Tracker` exposes `saveCredentials`, `getUsername`, `getPassword` — same API. No change needed beyond the import.

8. **`TrackSearch`**
   - komikku: `eu.kanade.tachiyomi.data.track.model.TrackSearch`.
   - Suwayomi has an equivalent search-result type used by Shikimori; import the Suwayomi one and adjust field names if they differ (verification step).

9. **Behaviour preserved exactly**
   - `buildRemoteId(source, mediaId)` hash, `buildTrackingUrl` / `parseTrackingUrl`, `resolveTotalChapters(maxProgress)` (maps `<=1` → `0`), ISO date helpers, `tracked`-flag check in `bind()`, `SOURCE_MANUAL` fallback entry in `search()`, integer `status` on PATCH/POST.

## Verification steps (to run while implementing, before opening the PR)

1. Clone `Suwayomi/Suwayomi-Server` locally (separate checkout from komikku).
2. Read `Shikimori.kt`, `ShikimoriApi.kt`, `ShikimoriInterceptor.kt` in full — they're the structural template. Confirm:
   - Exact `Track` import and field names.
   - How `TrackSearch` is constructed.
   - Whether `supportsReadingDates` exists on the base class.
   - The exact signatures of `update`, `bind`, `search`, `refresh`, `delete`, `login`.
3. Build the server: `./gradlew :server:build` — must compile.
4. Run the server locally, open Suwayomi-WebUI, and manually verify:
   - Login with a Yamtrack host URL + API token.
   - Search returns results + manual-entry fallback.
   - **Track** button adds an untracked manga (POST to `/media/manga/`).
   - Status, progress, score updates round-trip (PATCH → GET).
   - Progress accepts values > 1 on a manga with unknown chapter count.
   - Start/end dates persist (if the Track model supports them).
   - Unbind removes the entry from Yamtrack.
5. Run project linters: `./gradlew ktlintCheck` (or whatever Suwayomi uses — check `build.gradle.kts`).

## PR mechanics

- My GitHub MCP tools are scoped to `Breezyslasher/komikku` only, so I cannot push to `Suwayomi/Suwayomi-Server` or open the PR directly. The user will need to either:
  - Fork `Suwayomi/Suwayomi-Server` to their own account, let me push the branch to that fork (requires lifting the repo-scope restriction), then open the PR against upstream; or
  - Apply the changes locally themselves after I produce a patch file.
- PR title: **"Add Yamtrack tracker"**
- PR body: reuse the Yamtrack-as-a-whole summary already drafted in this session.

## Open question for the user

Does the user want me to:
- **(a)** produce the full set of Suwayomi-adapted source files inline in chat (user commits + opens PR manually), or
- **(b)** have me push to a fork they own that's been added to my allowed-repos scope?

Option (a) is always viable; option (b) needs a scope change before I can push.
