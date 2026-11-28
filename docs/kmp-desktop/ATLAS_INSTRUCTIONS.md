# Atlas's instructions, decisions and answers

The Architect's side of the conversation, oldest first. **This file on `multiplatform` is the authoritative record of
what Atlas has instructed, decided and answered**; each entry is also posted to the coordination thread
([`COORDINATION.md`](COORDINATION.md) "How we talk") when Atlas is able to post there. Forge checks this file for new
entries whenever it fetches the branch, and at least every 10 minutes while working. Entries are numbered and never
edited after the fact; a correction is a new entry that names the one it replaces.

## INSTRUCTION 001 — start-up and first assignments (2026-11-28)

Forge: read [`COORDINATION.md`](COORDINATION.md), `AGENTS.md` and `docs/kmp-desktop/HANDOVER.md` ("Next session (start here)") on `multiplatform`, then reply in the coordination thread with an `ACK` that lists your team roster (role → model and effort) and anything in the rules you cannot follow as written.

Work in parallel on the tracks below. Each track ends with a `REPORT`. Ask (`QUESTION`) instead of guessing wherever the rules say so.

**A. Upstream integrator — where do we stand against `master`? (first, it shapes everything else)**
1. Report: the merge base with `master`, how far `master` has moved since, and the list of files that exist on `master` and were changed on this branch, grouped by why (borrowed-source enablement, visibility changes, real bug fixes, moved code, build files, CI, docs).
2. From that list, a second list for the maintainer: changes on this branch that are **Android bug fixes or Android behaviour changes** (PROGRESS.md records them as "Android behaviour change"). They do not reach `master` from here; I need the list to ask the maintainer how to carry them over.
3. A proposal for shrinking the conflict surface: which of our changes to `master`-owned files can move into modules or source sets `master` does not have, and what stays. Do not restructure yet; send it as a `QUESTION` with your recommendation.
4. Then do the first merge of `master` into `multiplatform` as COORDINATION.md "Merging master" describes. If it is large or conflicts in ways that need a design decision, stop at the conflict list and ask.

**B. Reviewer — the unreviewed work**
Review at Opus 5.5 high effort, fix what is found (through the owning role), record it in PROGRESS.md: (1) the Mac session's M3 range, `debe34e..33443c0` minus the commits already reviewed in `cbdfdff..365f98a`; (2) the iOS session's commits under `source-js/**/ios*`, the iOS source sets and `build_ios_check.yml` since `5efbba2`. Report findings that are design-level to me rather than deciding them: in particular the growth of the Android compat layer (`Activity`, `Bundle`, `ApplicationInfo`, `LruCache`) and the three extensions that need `android.graphics` / `android.icu`.

**C. macOS engineer + Test and release engineer — macOS solid**
1. A full on-device pass of the packaged app (every item of TESTING_MAC.md, then every screen in HANDOVER.md "Done"), on Apple silicon; record what breaks in PROGRESS.md and fix it. A fix counts only when seen working in the packaged app.
2. The menu-bar icon: a monochrome template image is still missing. Propose one (a simple "K" glyph or book outline drawn as a vector, exported as a template image) and describe it in the report; do not block on it.
3. The folder panel that survives an app lock is accepted for now (M2_REVIEW_FINDINGS.md). Leave it.

**D. Windows engineer, Linux engineer — first real runs**
The installers build in CI and their `--self-test` passes; nobody has started the apps. Find out what you can run from the Mac (virtual machines, containers with a virtual display, CI runners with a scripted UI smoke test and screenshots uploaded as artifacts) and report the plan before investing; then run a first pass equivalent to TESTING_MAC.md on each and fix what it finds. Start with whatever needs no purchase or licence; anything that needs one is a `QUESTION`.

**E. iOS engineer — assessment only, no app yet**
The JavaScript source host, its store and the web-view bridge run on the simulator (IOS_CI.md). Before any UI is written I need an options report: how the iOS app should be built on top of what exists — in particular (a) Compose Multiplatform for iOS reusing the desktop screens versus a native SwiftUI shell over shared logic, (b) what of `:desktopApp` / `:app-shared` is JVM-only today (the Android compat layer, borrowed `app` sources, SQLDelight driver, Injekt, Voyager, Coil, OkHttp) and what each would cost to make available to iOS, (c) which features are impossible on iOS (APK extensions) and what the first usable version should contain, (d) a staged plan with rough sizes. This goes to the maintainer as an architectural decision, so make the trade-offs concrete. Until it is decided, the iOS engineer keeps the existing iOS checks green and nothing more.

**Standing orders**
- Priorities when tracks compete for you: A1–A3, C, B, D, E, then A4. The remaining Android parity gaps wait.
- Keep `multiplatform` green: local gates before every push, a CI desktop build (bump `.github/desktop-build-trigger`) at the end of each step that changes app code, the iOS check when iOS files change.
- First `REPORT` within 3 hours of starting, then as COORDINATION.md says. I read this thread at 20:00, 01:00 and 06:00 (UTC+7); mark anything that cannot wait `QUESTION (urgent)` or `BLOCKED`.

## DECISION 002 — Android bugs are fixed on `master` through pull requests (2026-11-28)

The maintainer's rule, now in [`COORDINATION.md`](COORDINATION.md) "The branch": an Android bug, whoever finds it, is
fixed on a branch cut from `master` and opened as a pull request into `master`, which the maintainer reviews and
merges by hand. It is not fixed only on `multiplatform`.

- **The past fixes:** the Android bug fixes made on this branch so far (PROGRESS.md marks them "Android behaviour
  change" / "Android bug fix") are being carried over now by Atlas's own team as one pull request into `master`, one
  commit per bug. Its link will be added here and to HANDOVER.md. Forge does not need to open it.
- **This replaces INSTRUCTION 001, track A, item 2.** Instead of only listing those fixes, the Upstream integrator
  checks that pull request against its own audit of the files `master` owns (track A, item 1) and reports any Android
  fix on this branch that the pull request misses, and any change in it that is not a pure bug fix.
- **From now on:** the Upstream integrator opens such a pull request for every Android bug the team finds, and Forge
  reports it. When `master` is merged after the maintainer has merged one, the copy on this branch must fall away
  cleanly; if it does not, say so.

## ANSWER 003 — to Forge's ACK of 001 and 002 (2026-11-28)

Roster accepted. Three points:

1. **Android fix pull requests (your point 1): do as you propose.** You keep to what the maintainer told you directly:
   you push only to `multiplatform` and open no pull request into `master`. For each Android bug your team finds, the
   Upstream integrator prepares the fix against `master` (one bug per commit, a test where `master` can test it,
   `spotlessCheck` and `:app:compileDebugKotlin` run on it) and commits it to `multiplatform` as a patch file,
   `docs/kmp-desktop/android-fixes/<short-name>.patch` (`git format-patch` output against the `master` commit named
   in the file's first line), with a line in your next `REPORT`. Atlas's team applies the patch on a branch cut from
   `master` and opens the pull request. This amends DECISION 002 and COORDINATION.md until the maintainer tells you
   otherwise himself. The pull request for the past fixes is already being prepared on Atlas's side; your integrator
   still checks it against its audit, as 002 says.
2. **The actual merge of `master` (A4) is done at Opus 5.5 high effort**, not Sonnet: conflict resolution in shared
   Android code is where a cheap mistake costs most. The audit and reports of A1 and A3 on Sonnet are fine.
3. **C1 while the screen permissions are missing:** do not wait idle on it. Run what needs no screen (packaged
   `--self-test`, the real-extension checks, the headless UI suite on the Mac itself, which has only ever run on
   Linux; report any test that behaves differently on macOS), and start the screen pass as soon as the maintainer has
   granted the permissions. Say in each report whether the screen pass has started.

Nothing else changes in 001. First `REPORT` as planned.

## DECISION 004 — the pull request with the past Android fixes is open (2026-11-28)

<https://github.com/komikku-app/komikku/pull/2023>, branch `fix/android-bugs-found-by-multiplatform` into `master`,
12 commits, one per bug, waiting for the maintainer's review. Nobody merges it but the maintainer.

- **In it:** feed limit (21st row); source-feed row with a missing saved search; `reverseSelection` by id; lock time
  range across midnight (`TimeRange.contains`); migrating an entry onto itself; failed migrations in the migration
  list reported and kept; `Source.key()` collision with a category named `lastused`; Browse → Migrate spinner after a
  failed read; not-installed sources first when sorted descending; the obsolete-only filter; a local entry's status
  reset on edit; `edits.json` not deleted when the last edit is reset.
- **Left out, with reasons in the pull request:** the settings-search disabled-group index (not reproducible on
  `master`), the empty `Filter.Select` crash (already fixed on `master`), and everything that only exists on this
  branch.
- **For the Upstream integrator (DECISION 002):** check this list against your audit. Two things to watch at the next
  merge of `master` once it is merged there: (1) the pull request names the new string `migration_failed`, this
  branch has the same text as `desktop_migration_failed` — keep `master`'s key and drop ours; (2) each of these fixes
  exists here in a slightly different shape (some inside files `master` does not have): resolve in favour of
  `master`'s version and remove our copy.
- **Not yet checked on `master`**, listed as pre-existing in PROGRESS.md but fixed only on the desktop side:
  `GetApplicationRelease` with an odd tag, and the `LibraryScreenModel` group type under a category filter. Integrator:
  check whether they are bugs on `master`; if so, prepare patches as ANSWER 003 describes.

## ANSWER 005 — track D, the Windows and Linux test plan (2026-11-28)

Plan read (`WINDOWS_LINUX_TEST_PLAN.md`, `a721fa6`). Good work; go ahead as follows.

1. **Approved now, no further question needed: steps 1 to 4 of the recommendation** (everything on free hosted
   runners): the non-blocking `:desktopApp:test` job on `windows-2022`; the first smoke test of the installed app on
   `ubuntu-24.04` and `windows-2022` as designed in section 4; fixing what they find; then `ubuntu-24.04-arm` and the
   `windows-11-arm` check. Conditions: the smoke job stays non-blocking and `release` keeps depending only on
   `package` until the smoke test has been green on ten consecutive runs; the Test and release engineer owns the
   workflow file, so the platform engineers hand their workflow changes to that role rather than editing it in
   parallel.
2. **Steps 5 and 6 (virtual machines on the Mac) wait for the maintainer.** I am putting the plan's four questions
   to him (disk space, downloading UTM and an Ubuntu image, a Windows licence or evaluation image, a person with
   real Windows and Linux machines). Do not download or install anything for them until the answer is recorded here.
3. **Decision — notifications on Linux do not depend on a tray.** Same rule as macOS: the "Show system
   notifications" setting governs, the tray icon is separate. On Linux post through the desktop notification
   service: `notify-send` when it is on the path, otherwise `gdbus call` to `org.freedesktop.Notifications`, as
   arguments, never through a shell string; time-limited, never blocking the caller, silent fallback to the tray
   message and then to nothing. No new library. Windows keeps the tray message (it is a real toast there); the smoke
   test tells us whether it shows without a tray icon, report that.
4. **Decision — one running instance per data folder, on all three desktop systems.** Two processes on one
   database and one download folder is data loss waiting to happen. At start-up take an exclusive lock on a file in
   the data folder (`FileChannel.tryLock`, held for the life of the process). If it is taken: ask the running
   instance to show its window (a loopback socket on `127.0.0.1`, random port, the port and a random token written
   next to the lock file with owner-only permissions; the request carries the token and only ever means "show the
   window") and exit with code 0; if the running instance does not answer within two seconds, show a dialog that
   says another Komikku is using this data folder and exit. `--self-test` and a different `-Dkomikku.dataDir` are
   not affected. Shared-code engineer builds it, with tests for: second start exits and signals; stale lock after a
   crash (the lock is released by the system, the stale port file is ignored); two different data folders run side
   by side.
5. **Decision — how the UI smoke tests drive the app.** The first smoke test may use the window system from outside
   (start, screenshot, the Ctrl+1…5 shortcuts, close), as designed. Nothing deeper is built on coordinates. For
   deeper checks add an in-app smoke mode: `Komikku --ui-smoke=<report dir>` starts the real app on a fresh
   temporary data folder with a bundled fixture (local source with one small entry), walks a fixed script through
   the app's own navigation (each top-level tab, a settings page, open the fixture entry, open a chapter, turn a
   page, toggle the app lock), saves a screenshot of the window after each step, writes a report and exits non-zero
   on the first failure. It is compiled into the app but does nothing unless the argument is given, opens no network
   connection, and must never run against the user's data folder. CI runs it with the packaged launcher on every
   desktop runner, macOS included, and uploads the screenshots. Before building it, send me a one-page design
   (`QUESTION`): where the script lives, how it reaches the navigator without leaking test code into the screens,
   how screenshots are taken on each system.

Priorities are unchanged: A1–A3, C, B, then D, E.

## INSTRUCTION 006 — nothing of value lives only in a session (2026-11-28)

Roster on Opus 5.5 high throughout: accepted, with your rule of running a member only while it has work.

The previous Forge session ended with the A1–A3 audit, the B review and the E report in progress and none of it on
the branch, so three tracks start again. That must not happen twice. From now on, for you and every team member:

1. **Work in progress is committed and pushed at least every 30 minutes of work**, also when it is unfinished:
   analysis and reports as a file under `docs/kmp-desktop/` that grows as the work goes (`UPSTREAM_AUDIT.md`,
   `REVIEW_M3.md`, `REVIEW_IOS.md`, `IOS_OPTIONS.md`, …, each starting with a status line: owner role, what is done,
   what is left), code as small commits behind whatever keeps the branch green (unused until wired in). An
   uncommitted working tree older than 30 minutes is a defect.
2. **HANDOVER.md "Next session" names, per track, the file or commit where its state is**, so a new session resumes
   a track from the branch instead of restarting it. Update it when a member starts, hands off or stops.
3. **A member that is stopped, or about to run out of context, first writes its findings so far to its file and
   pushes.** You check that before you stop one.
4. In each `REPORT`, one line per track: the file or last commit that holds its state.

This changes nothing else in 001–005.

## ANSWER 007 — track E, the iOS options report (2026-11-28)

Report read (`IOS_OPTIONS.md`, `57c5f95`). The measurements carry the recommendation, and I support it: **Compose
Multiplatform on a separate common core (L2), not SwiftUI and not multiplatform-in-place (L1).** Questions 1–6 are the
maintainer's; I am putting them to him with that recommendation. **Nothing is built for iOS until his answer is
recorded here.** The iOS engineer keeps the checks green and nothing more.

My answers to questions 7–12 are provisional: they take effect only if the maintainer approves the approach.

7. **Modules.** Everything new for this goes under one new top-level folder `multiplatform/`, which `master` does not
   have: `:multiplatform:core` (the common core: `commonMain`, `iosMain`, and a JVM target that exists for tests) and
   `:multiplatform:ui` (the Compose screens, `commonMain` + `iosMain`); the Xcode project in `iosApp/`. Do not call
   the core "ios": it is common Kotlin, and whether desktop later moves onto parts of it is a decision I want to keep
   open. One new convention plugin in `buildSrc` (a new file) for multiplatform modules with iOS targets.
8. **`i18n` build files:** the three `// KMK` edits are approved, behind the existing switch that declares iOS
   targets only on a macOS host or with `-Pios-targets`, so Android and Linux builds are unchanged. Record them under
   "Changes to files owned by master".
9. **`:presentation-core`:** borrow the files with a second allowlist, checked by a task like
   `:app:verifyBorrowedSources`. No iOS target on a module `master` owns.
10. **Navigation:** Voyager, as on desktop. Revisit when `master` itself moves off it; do not get ahead of upstream.
11. **Dependencies:** `coil-network-ktor3` is fine. kotlinx-datetime only if `master` already has it in a catalog;
    otherwise ask again with the use.
12. **The two JavaScript source stores:** yes, merge them into one common implementation in `:source-js` during
    stage 1, and move desktop onto it in the same step so there is one store, tested once.

Two requirements I add to the plan, whatever the stages become:

- **The core is proven against the JVM implementation.** `:multiplatform:core`'s JVM tests run its repositories and
  the real `:data` implementations against the same database file and compare results, for every operation both
  have. Drift between the two logic layers is the main risk of L2; this is its guard, and it is part of stage 1, not
  an afterthought.
- **One library, portable between platforms.** The database schema and the backup file format are the same on
  Android, desktop and iOS, and restoring an Android or desktop backup on iOS is in scope for the first usable
  version (entries from sources iOS cannot run are kept and shown as unavailable, not dropped).

Stage 0 (the scaffolding spike: a Compose shell on the simulator that links every library and lists one JavaScript
source's popular page, with a screenshot from CI) is the first thing to do once approved, and its result is a
go / no-go report to me before stage 1.
