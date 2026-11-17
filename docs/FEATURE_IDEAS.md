# Komikku feature ideas

Brainstorm from a product-team view: Senior Android Developer, Mobile UX Designer, Engagement Designer.
Nothing here is implemented. No code was changed for this document.

## 1. What already exists (so we don't re-propose it)

Checked in the codebase before writing this list.

| Area | Already there |
|---|---|
| Reader | Webtoon, paged and continuous-vertical modes; zoom, crop borders, dual-page split, color filter, grayscale, custom brightness; page-change flash; configurable bottom bar (`ReaderBottomButton`); share, save and copy-page actions (`ReaderPageActionsDialog`); volume keys and tap zones; **auto-scroll with speed (this project)** |
| Library | Display modes, badges, categories, filters (downloaded, unread, started, bookmarked, completed, interval), random sort, "continue reading" button, swipe actions, group by |
| Discovery | Feed tab and saved searches, related titles, recommendations, migration, global search, MangaDex and E-Hentai integrations |
| Stats | `StatsScreen` has only totals: library count, completed, total read time, chapters read, downloads, tracker mean score |
| Personalization | About 20 color themes, AMOLED dark, cover-based theme, custom theme colors |
| Social-light | Per-title notes (markdown), Discord rich presence, trackers (AniList, MAL, Kitsu, etc.), upcoming-releases calendar |
| Tech | Backups, data saver, 2 home-screen widgets (updates grid) |

**Not found:** streaks, goals, achievements, year in review, per-day stats, TTS or any accessibility reading aid, a "continue reading" launcher shortcut (four static shortcuts exist: library, updates, history, browse), per-chapter notes, reading-time estimates, or sleep timer.

### Data constraint that shapes the stats ideas
`history` stores **one row per chapter**: `last_read` and a cumulative `time_read`. There is no per-day log. So:
- Streaks and "days read" can be approximated from `last_read` (it only keeps the latest read of each chapter).
- Reading time per day, per hour or per session **cannot** be computed without a new table. Ideas 8 to 12 therefore share a small prerequisite: a `reading_log` table (day, seconds, pages, chapters), filled by the reader. That needs a SQLDelight migration and a backup-model field, which is the main upstream-conflict risk.

## 2. Ideas

Scores: **F** = fun, **U** = usefulness (1 to 5). Effort: S under about 2 days, M about 3 to 7 days, L over a week. "Upstream" means the risk of merge conflicts with Mihon/SY.

### A. Reading experience

**1. Sleep timer / stop after N chapters**
- *What:* Auto-scroll and reading stop after a chosen time or number of chapters, with a gentle fade and a toast.
- *Why:* Night readers fall asleep with the screen on. It builds directly on the new auto-scroll.
- *Touches:* `ReaderViewModel`, reader settings sheet, auto-scroll flow in `ReaderActivity`.
- *Effort:* S. *Risks:* clutter (one more setting), none for battery. Upstream: low. *F* 2 *U* 4

**2. Per-title reader settings memory**
- *What:* Remember brightness, color filter, and auto-scroll speed per title or per source, not only reading mode.
- *Why:* A dark-art series and a bright one need different filters. Speed of reading differs by content density.
- *Touches:* `ReaderPreferences`, manga viewer flags (`viewer_flags` exists), `ReaderViewModel`.
- *Effort:* M. *Risks:* schema/backup change if stored in the DB; settings sprawl. Upstream: medium. *F* 2 *U* 4

**3. Reading-time estimate**
- *What:* "About 12 min left" in the chapter list and reader header, learned from the user's own average seconds per page.
- *Why:* Helps decide whether to start a chapter. Uses data already in `history.time_read`.
- *Touches:* chapter list item, reader page-number label, history.
- *Effort:* S. *Risks:* inaccurate for the first few chapters; keep it approximate. Upstream: low. *F* 3 *U* 3

**4. One-hand "edge swipe" brightness and speed**
- *What:* Vertical swipe on the screen edge adjusts brightness; in auto-scroll, a swipe on the other edge adjusts speed (reusing the live-adjust hook).
- *Why:* Fewer menu trips while reading. Natural extension of the new speed setting.
- *Touches:* `WebtoonFrame` / viewer touch handling, `ReaderPreferences`.
- *Effort:* M. *Risks:* gesture conflicts with zoom, pan and the status-bar swipe. Needs a toggle, default off. Upstream: medium. *F* 3 *U* 3

**5. Reader "peek" of next chapter's first page in the transition**
- *What:* The chapter-transition page shows a small blurred preview of the next chapter's first page.
- *Why:* Smooths the end-of-chapter dead stop; hints at what's next.
- *Touches:* `ChapterTransition` holder in webtoon and pager viewers.
- *Effort:* M. *Risks:* extra network or cache load on metered data; only use already-preloaded pages. Upstream: medium. *F* 3 *U* 2

**6. Text-to-speech for translated or novel-like sources (OCR not included)**
- *What:* Read chapter titles and notes aloud, or use a source's text pages if it provides them.
- *Why:* Accessibility.
- *Touches:* reader. *Effort:* L for real value. *Risks:* few sources give text; OCR would be a heavy dependency and break the "no new deps" ethos. *F* 2 *U* 2. **Listed for completeness; not recommended.**

### B. Library and discovery

**7. "What should I read?" tonight picker**
- *What:* One button that suggests a title from the library using filters (unread count, time available, last read, category), with a "reroll".
- *Why:* The library often becomes a backlog. A random sort already exists, but this uses context and has a playful result.
- *Touches:* `LibraryScreenModel` (has `getRandomLibraryItemForCurrentCategory`), library toolbar.
- *Effort:* S. *Risks:* clutter in the toolbar; hide it in the overflow menu. Upstream: low. *F* 4 *U* 3

**8. Backlog and "stale" shelf**
- *What:* A virtual category that shows titles not opened for N months, with one-tap "drop or keep".
- *Why:* Keeps the library clean; uses `history.last_read` and chapter data.
- *Touches:* library filter, category logic. *Effort:* M. *Risks:* long-library performance. Upstream: medium. *F* 2 *U* 4

**9. Release-day reminders (opt-in, local)**
- *What:* Notification on a chosen weekday or time for titles whose fetch interval predicts a release, using the existing `FetchInterval` and calendar.
- *Why:* The upcoming calendar exists, but nothing actively nudges.
- *Touches:* `mihon/feature/upcoming`, `FetchInterval`, a WorkManager job, notification channel.
- *Effort:* M. *Risks:* battery (use batched WorkManager, no exact alarms), notification fatigue. Upstream: low (separate package). *F* 3 *U* 4

**10. Library "collections" shelf with tags you define**
- *What:* Free-form personal tags on titles (mood, "weekend read") that are separate from categories, and filter by them.
- *Why:* Categories are exclusive in practice. Tags are cheap and flexible.
- *Touches:* new column or table, library filter, edit dialog, backup.
- *Effort:* L. *Risks:* schema + backup; high upstream conflict. *F* 3 *U* 4

### C. Stats and motivation

All of these need the `reading_log` prerequisite (see section 1), except streaks, which can start from `last_read`.

**11. Reading streak with grace day**
- *What:* Counts consecutive days with at least one chapter read; one "grace" day per week so it isn't punishing. Shown in the stats screen and optionally as a small flame badge.
- *Why:* Light habit loop. Easy to switch off.
- *Touches:* `StatsScreenModel`, `StatsData`, history query.
- *Effort:* S (from `last_read`) or M (with the log). *Risks:* guilt mechanics; keep opt-in, gentle copy. Upstream: low. *F* 4 *U* 3

**12. Daily and weekly goals**
- *What:* Goal in chapters or minutes per day/week; ring on stats and an optional quiet notification.
- *Why:* Core motivation tool. The auto-scroll speed also lets us show "pages per minute".
- *Touches:* new prefs, stats screen, `reading_log`. *Effort:* M. *Risks:* notification annoyance; allow zero notifications. Upstream: low. *F* 4 *U* 3

**13. Reading heatmap (GitHub-style calendar)**
- *What:* 12-month grid of days colored by reading amount, tap a day for the titles read.
- *Why:* The most satisfying single visual for a reader; also useful as history.
- *Touches:* stats screen, Compose canvas, `reading_log`.
- *Effort:* M. *Risks:* rendering cost is trivial (365 cells); dark/AMOLED color contrast. Upstream: low. *F* 5 *U* 3

**14. Achievements (local badges)**
- *What:* About 20 badges: first 100 chapters, a 7-day streak, finish a 50-chapter series, read in 3 reading modes, and so on. A "trophies" page, no popups unless enabled.
- *Why:* Playful, discoverable, and nudges people to use features such as trackers and auto-scroll.
- *Touches:* new `achievements` domain object evaluated from stats; stats screen; backup (optional).
- *Effort:* M to L. *Risks:* scope creep, stale badge rules; ship with a small fixed set. Upstream: low. *F* 5 *U* 2

**15. Year in review ("Komikku Wrapped")**
- *What:* Shareable summary: total hours, chapters, top 5 titles, top genres/tags, longest streak, busiest month. Built from the local log and rendered to an image.
- *Why:* Highest delight and organic sharing, entirely offline.
- *Touches:* stats, new composable rendered to bitmap, share intent (already used for pages).
- *Effort:* L. *Risks:* needs one full year of log (can bootstrap from `last_read` for a rough first version); privacy: share only on tap, and offer "hide titles". Upstream: low. *F* 5 *U* 3

**16. Smarter stats: genres, sources, completion rate**
- *What:* Breakdown by genre/tag, source, and status; "average chapters per sitting".
- *Why:* The current screen has only totals. This is mostly queries over existing tables.
- *Touches:* `StatsData`, `StatsScreenModel`, `StatsScreenContent`.
- *Effort:* S to M. *Risks:* query cost on a large library; run on IO and cache. Upstream: low. *F* 3 *U* 3

### D. Personalization and themes

**17. Scheduled theme and reader filter (sunset/sunrise)**
- *What:* Switch app theme and reader color filter by time of day, optionally with a warm "night" preset.
- *Why:* The reader already has a color filter and AMOLED mode, so this is a scheduler plus presets.
- *Touches:* `UiPreferences`, `ReaderPreferences`, a small time-based flow. *Effort:* S to M. *Risks:* conflicts with the user's manual choice; "manual override until next change". Upstream: low. *F* 3 *U* 3

**18. Reader theme packs (paper, sepia, high-contrast)**
- *What:* Named presets bundling background color, filter, and brightness, shared as a small JSON string.
- *Why:* Easy personalization; ties to idea 23.
- *Touches:* `readerTheme`, color filter prefs. *Effort:* S. *Risks:* low. Upstream: low. *F* 3 *U* 2

**19. Custom bottom-bar and gesture layout presets**
- *What:* Save and switch sets of reader bottom buttons and tap zones, such as "commute" and "bed".
- *Why:* `ReaderBottomButton` is already configurable; presets make switching fast.
- *Touches:* `readerBottomButtons`, navigation prefs, settings UI. *Effort:* M. *Risks:* settings clutter. Upstream: medium (SY-owned code). *F* 2 *U* 3

### E. Shareable and social-light (no server)

**20. Shareable title cards**
- *What:* Share an image card: cover, title, your rating, one-line note, and progress. Exported via the standard share sheet.
- *Why:* Recommending without a backend; reuses notes and tracker scores.
- *Touches:* manga screen overflow, bitmap rendering, share intent. *Effort:* M. *Risks:* copyright: share only the user's own cover already on-device, and let the user hide it. Upstream: low. *F* 4 *U* 2

**21. Export/import a "reading list" as a file or link**
- *What:* Share a list of titles (name + source + URL) as a small file or text; the receiver can search for them in their sources.
- *Why:* Friend-to-friend recommendations; also useful for migrating between devices without the full backup.
- *Touches:* backup models (lighter variant), deeplink handling (`ui/deeplink` exists). *Effort:* M. *Risks:* source mismatch (extensions differ); do a search-by-title fallback. Upstream: low. *F* 3 *U* 4

### F. Quality of life

**22. Launcher shortcuts and "continue reading" widget**
- *What:* Long-press the icon to jump to the last-read title, the library, or updates; plus a one-tile widget for the last read title. Four static shortcuts already exist (`app/shortcuts.xml`), but none resumes reading.
- *Why:* Saves taps for the most common action; the existing widget code (Glance) can be reused.
- *Touches:* a manifest `shortcuts.xml` or dynamic shortcuts, `presentation-widget`.
- *Effort:* S. *Risks:* low. Upstream: low. *F* 2 *U* 5

**23. Per-chapter notes and bookmarks with a note**
- *What:* Attach a short note to a chapter or even a page ("Fight starts here"). Notes at title level exist, but not chapter level.
- *Why:* Useful on long series and helps people who re-read.
- *Touches:* chapter model and DB (`memo` column exists on chapters; check its current use before adding a column), reader page actions.
- *Effort:* M. *Risks:* schema, backup; upstream: high. *F* 2 *U* 3

**24. Smart "storage cleanup" and read-chapter auto-delete preview**
- *What:* A page that lists the largest downloads and chapters already read, with the space freed shown before you confirm.
- *Why:* Very practical on phones; builds on download-after-read settings.
- *Touches:* `download` domain, storage settings screen. *Effort:* M. *Risks:* destructive: confirmation and undo-friendly wording. Upstream: low. *F* 1 *U* 4

### G. Playful details (all default off or very subtle)

**25. Confetti or sparkle on "series completed"**
- *What:* A brief, optional animation when the last chapter of a completed title is read.
- *Why:* Marks a small milestone at near-zero cost.
- *Touches:* reader end-of-series path (same place where the menu opens at the end of a series), Compose animation.
- *Effort:* S. *Risks:* none beyond taste; setting "Celebrations: off". Upstream: low. *F* 4 *U* 1

**26. Seasonal and event app icons / themes**
- *What:* Optional alternate launcher icons and a couple of themes for holidays.
- *Why:* Pure charm. Alternate icons are done with `activity-alias`.
- *Touches:* manifest, appearance settings. *Effort:* S. *Risks:* maintenance; build size. Upstream: low. *F* 3 *U* 1

**27. Reader "pet" or mascot milestones**
- *What:* The existing mascot (Tako) shows a small message at milestones in the stats page, such as "100 chapters this month".
- *Why:* Gives achievements and streaks a friendly voice. Optional.
- *Touches:* stats screen only. *Effort:* S (after 11 to 14). *Risks:* tone; keep silent mode. Upstream: low. *F* 4 *U* 1

## 3. Ranking

Value score = (F + U) ÷ effort weight, where effort S = 1, M = 2, L = 3. Higher means more value for the cost. Ties are broken by lower upstream risk.

| Rank | # | Idea | F | U | Effort | Value |
|---|---|---|---|---|---|---|
| 1 | 22 | Launcher shortcuts and continue-reading widget | 2 | 5 | S | 7.0 |
| 2 | 7 | "What should I read?" picker | 4 | 3 | S | 7.0 |
| 3 | 11 | Reading streak with grace day | 4 | 3 | S | 7.0 |
| 4 | 1 | Sleep timer / stop after N chapters | 2 | 4 | S | 6.0 |
| 5 | 3 | Reading-time estimate | 3 | 3 | S | 6.0 |
| 6 | 25 | Confetti on series completed | 4 | 1 | S | 5.0 |
| 7 | 16 | Smarter stats (genres, sources) | 3 | 3 | S–M | 4.0 |
| 8 | 18 | Reader theme packs | 3 | 2 | S | 5.0 |
| 9 | 17 | Scheduled theme and filter | 3 | 3 | S–M | 4.0 |
| 10 | 13 | Reading heatmap | 5 | 3 | M | 4.0 |
| 11 | 9 | Release-day reminders | 3 | 4 | M | 3.5 |
| 12 | 12 | Daily and weekly goals | 4 | 3 | M | 3.5 |
| 13 | 21 | Reading-list export/import | 3 | 4 | M | 3.5 |
| 14 | 24 | Storage cleanup assistant | 1 | 4 | M | 2.5 |
| 15 | 2 | Per-title reader settings memory | 2 | 4 | M | 3.0 |
| 16 | 20 | Shareable title cards | 4 | 2 | M | 3.0 |
| 17 | 14 | Achievements | 5 | 2 | M–L | 2.8 |
| 18 | 15 | Year in review | 5 | 3 | L | 2.7 |
| 19 | 8 | Stale shelf | 2 | 4 | M | 3.0 |
| 20 | 4 | Edge-swipe brightness / speed | 3 | 3 | M | 3.0 |
| 21 | 19 | Layout presets | 2 | 3 | M | 2.5 |
| 22 | 26 | Seasonal icons | 3 | 1 | S | 4.0 |
| 23 | 27 | Mascot milestones | 4 | 1 | S | 5.0 |
| 24 | 23 | Chapter notes | 2 | 3 | M | 2.5 |
| 25 | 5 | Next-chapter peek | 3 | 2 | M | 2.5 |
| 26 | 10 | Personal tags | 3 | 4 | L | 2.3 |
| 27 | 6 | Text-to-speech | 2 | 2 | L | 1.3 |

(The raw value column favors small ideas. The picks below also weigh strategic fit, so they are not strictly the top of the table.)

## 4. Recommended picks

### Top 5 best-value

1. **#22 Launcher shortcuts + continue-reading widget.** Highest everyday usefulness for the least effort. No shortcut resumes reading yet.
2. **#11 Reading streak with grace day.** Can ship from existing `last_read` data and creates the seed for the whole stats and motivation family.
3. **#7 "What should I read?" picker.** Tiny change on top of existing random-selection code, and charming.
4. **#1 Sleep timer.** Finishes the auto-scroll story: auto-scroll without a stop control is a battery and sleep problem.
5. **#3 Reading-time estimate.** Uses existing history data and makes every chapter list more informative.

### 3 quick wins (each under about 2 days)

- **#22 Launcher shortcuts.**
- **#1 Sleep timer.**
- **#7 "What should I read?" picker.**

### 2 ambitious

- **#15 Year in review**, with **#13 heatmap** and the `reading_log` table as foundation. It is the biggest delight and sharing feature, and it works offline. It needs the new table, a backup field, and a rendering pipeline.
- **#9 Release-day reminders** (or **#10 personal tags** as the alternative). Reminders make the existing calendar active, but need careful battery behavior with WorkManager.

## 5. Suggested build order

1. **Sprint 1: quick wins.** #22 shortcuts, #1 sleep timer, #7 picker. No schema changes, low upstream risk.
2. **Sprint 2: stats foundation.** #3 reading-time estimate, #11 streak (from `last_read`), #16 smarter stats. Also add the `reading_log` table and the reader hook that fills it, so history starts accumulating early. Data collected sooner means a better Year in review later.
3. **Sprint 3: motivation.** #13 heatmap, #12 goals, #27 mascot milestones, #25 confetti. Everything switchable in one "Motivation" settings group.
4. **Sprint 4: ambition.** #15 Year in review (can be demoed from a partial year), #14 achievements.
5. **Later or on demand.** #9 reminders, #21 reading-list export, #2 per-title memory. Anything needing schema and backup changes (#10, #23) should wait until the `reading_log` migration has proven stable.

### Cross-cutting guidelines
- Put everything under `// KMK -->` markers and `KMR` strings, as with auto-scroll, to keep merges with upstream clean.
- Every playful or motivational item gets a single off switch. A "Motivation" group in Settings is simplest.
- No network use for any idea above. Sharing goes only through the Android share sheet and only on tap.
- Prefer new files and packages (like `mihon/feature/upcoming`) over editing shared screens.
