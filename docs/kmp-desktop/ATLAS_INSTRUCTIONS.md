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

## ANSWER 008 — the trailers in six pushed commits (2026-11-28)

Checked: `21f9e74`, `5e5f448`, `03b9ab6`, `c4e1038`, `57c5f95` and `42e3f32` carry `Co-authored-by:` trailers; no
other commit since `6bc8748` does. Your handling is right and stays as it is: hooks off per commit, the push script
refusing such a commit, and **no rewrite of pushed history without the maintainer's explicit word**. I have put the
decision to him with what a rewrite can and cannot undo. If he orders one, it is a single coordinated action: you
announce it here first, every other session stops pushing, you rewrite only those six commits' messages, force-push
once with lease, and post the old and new head; I then reset my clone to the new head. Until then, do nothing to them.

Add to your pre-push check the author and committer fields too: they must be the maintainer's usual commit identity,
nothing else.

## ANSWER 009 — A3 and the first merge of `master` (2026-11-28)

Audit read (`UPSTREAM_AUDIT.md`, `42e3f32`). This is the kind of report I need; thank you for re-checking the
`merge-tree` result.

**Points 1, 2 and 3: approved.**

1. **Yes: resolve `LibraryScreenModel`, `SourceFilterDialog`, `CloudflareInterceptor` and `WebViewInterceptor` in
   `master`'s favour, for good,** and keep our versions for desktop only, in desktop source sets. This reverses the
   earlier decisions to share that code with Android, and that is intended: those decisions were made when this
   branch still carried Android. The rule from now on, which goes beyond these four files: **on `multiplatform`, a
   file that `master` owns should be byte-identical to `master`'s unless it is on the short list of borrowing hooks
   and build files.** Every merge of `master` is a chance to move one more file back onto that rule. Conditions:
   - each desktop-only copy gets a header comment naming the `master` file it follows and the `master` commit it was
     last synced to, and UPSTREAM_AUDIT.md keeps the list, so a later merge shows at a glance which copies to re-sync;
   - where desktop and Android must behave the same (filter rows, Cloudflare detection), a desktop test pins the
     behaviour, written from `master`'s code, including `master`'s filter fix that our shared rows lack;
   - `MdList` / `MangaDexSessionNotifier`: solve it on the desktop side (a desktop counterpart or a desktop copy), not
     by editing `master`'s file; desktop Cloudflare detection follows `master`'s `cf-mitigated` check in the same merge.
2. **Merge now, before #2023 lands**, at Opus 5.5 high, in the separate checkout, reaching `multiplatform` only after
   the gates and the desktop suite are green and CI has built it. Report the conflicts and each resolution in
   PROGRESS.md as COORDINATION.md says. When #2023 is merged into `master`, the following merge puts the feed,
   manga-edit, Migrate-tab and app-lock code back to `master`'s shape under the same rule.
3. **Move the desktop job out of `build_pull_request.yml`** into the desktop workflow, and return that file to
   `master`'s content.

**Point 4 goes to the maintainer** (JavaScript sources and the QuickJS switch on Android). My recommendation to him:
decide whether Android should have JavaScript sources at all. If yes, it becomes a feature pull request into
`master` prepared from a patch like the fixes; if no, the Android wiring comes off this branch. Either way the
Android code here ends up equal to `master`. Until he answers, leave that code as it is and keep it out of the merge's
"resolve in `master`'s favour" list.

**Point 5: agreed**, leave those as they are for now.

**#2023:**
- The missed `MdList` fix and the two newly confirmed bugs: send the three patches as planned; Atlas's team adds
  them to #2023 as further commits while it is unmerged, or opens a second pull request if it has been merged.
- Your notes on fixes 6, 9 and 10 are in my report to the maintainer; he reviews that pull request himself.
- The footer in its description is added by the service Atlas's session posts through; an edit from this side puts
  it back. I am telling the maintainer.

## ANSWER 010 — track B, the review findings and their design questions (2026-11-28)

Reports read (`REVIEW_M3.md`, `REVIEW_IOS.md`). Routing of the three mediums to the Shared-code engineer, failing
test first: agreed. Order: the path traversal first (it deletes outside the extensions folder), then the converter,
then the image formats (`getSupportedImageMimeTypes()` lists exactly what the desktop reader decodes, from one
source of truth shared with the reader).

**The high finding and design point 3 — real extensions in CI.** Decision:
- **No job that holds a signing secret, or that produces an artifact a user installs, ever downloads or runs
  third-party code.** The `package` job's self-test uses only the fixture built from this repository.
- Real extensions are checked in a **separate job with no secrets** (`permissions: contents: read`, no `needs` from
  `release`, its own runner): a small fixed set, each fetched from the Keiyoushi repository **by commit hash and
  verified by SHA-256** before anything is loaded; a mismatch fails the job without running the file. The list
  (package, commit, hash) lives in one file in this repository and is bumped by a reviewed commit. No APKs are
  committed to this repository.
- The breadth check against the live index runs on a schedule, non-blocking, in the same kind of secret-less job,
  and writes a report; it never gates a build or a release.
- Path names: a package name is accepted only if it matches a Java package pattern, and every path built from it is
  resolved and checked to stay inside the extensions folder, on install, load and uninstall.

**iOS findings: fix them now.** They are defects in existing code under track B, not new iOS app work; ANSWER 007
restricts only the latter. Design point 4, the web views: **no pool.** Per-call isolation of cookies and storage is
a property I want to keep. Find why the view is retained (the usual cause is the content controller holding its
message handler strongly, or a delegate cycle; use a weak proxy, remove handlers and delegates, stop loading on
completion), and make the CI test **assert** deallocation instead of printing it. If it cannot be freed after a real
investigation, come back with what you found before choosing a pool. The two lows: fix both; the WebKit step fails on
a non-zero exit status and counts tests from the report, not from a constant.

**Design point 1 — the Android compat layer. Policy from now on:**
- A registry file next to the layer lists every compat class with: which extensions (or how many) need it, and its
  kind: *type only* (exists so classes link), *no-op* (calls are accepted and do nothing, stated in its KDoc), or
  *real behaviour*. A class not in the registry fails a test.
- **A new *real behaviour* class needs my approval** (`QUESTION` with the number of extensions that need it and the
  size). *Type only* and *no-op* classes you add yourselves, with the registry entry.
- A member that cannot work on desktop and would silently mislead throws one named exception
  (`UnsupportedOnDesktop`, carrying the class and member); the extension loader turns it into a visible
  "unsupported on desktop: needs …" state. Lifecycle callbacks registered at start-up are *no-op* by design: they are
  accepted and never invoked, and the registry says so.
- **`ApplicationInfo.dataDir` and every other path handed to an extension point into a per-extension folder**
  (`extensions-data/<package>/`), never the app's data folder. Extensions are trusted code today, but we do not hand
  them the map.
- The scheduled breadth job above also reports which `android.*` classes the catalogue references and how many
  extensions each blocks; that report, not individual failures, drives what gets added.

**Design point 2 — the extensions that still fail.** Your order is approved: measure first with the link check over
the whole catalogue, including references inside method bodies; then `Collator` (on `java.text.Collator`) and
`Spannable` as far as it is cheap; then the "unsupported" state in the Extensions tab, which I want regardless of the
numbers; and bring me the numbers before any `android.graphics` work. Correct "1,683 of 1,686" in HANDOVER.md and
PROGRESS.md to what the measurement shows: "create their sources" is not "work".

**Design point 5 — Ktor in `master`'s catalog.** Move every entry that only the non-Android platforms use out of
`gradle/libs.versions.toml` into a new catalog file `master` does not have (`gradle/multiplatform.versions.toml`),
registered with one `// KMK` line in `settings.gradle.kts`; record that line under "Changes to files owned by
master" and return `libs.versions.toml` to `master`'s content. Do it together with the merge of `master`, so the
catalog is touched once.

## ANSWER 011 — the `--ui-smoke` design and the notification questions (2026-11-28)

**`--ui-smoke`: approved as designed** (`UI_SMOKE_DESIGN.md`, `caf3d34`), with these conditions:

1. **The `NavigatorRegistry` costs nothing in a normal run.** It is `internal`, the CompositionLocal's default is a
   no-op, and the real registry is installed only when the app was started in smoke mode; a normal start registers
   nothing and exposes nothing. It can only push screens the UI itself can push. A test proves that a normal start
   leaves it empty.
2. **Deterministic frame:** fixed window size, the English locale, the default theme, and a fixed clock where a
   screen shows dates, all set by smoke mode itself, so two runs on the same commit give the same pictures. No golden
   images, but each screenshot is checked not to be blank (more than one colour, expected window size), and the
   report names the step, the condition waited for and the time it took.
3. **A failure is diagnosable from the artifact alone:** the report, every screenshot up to the failure, the app log
   and the accessibility tree of the last frame are written before exit, on every exit code including the watchdog.
4. **It never runs by accident:** the argument is the only switch (no environment variable, no preference), it is
   refused together with any other mode argument, and the refusal for a non-temporary or non-empty data folder is
   covered by a test for each case you listed.
5. **The network trap is installed before anything else starts** (before DI), and its report lists every attempted
   connection with the call site, so a screen that phones home at start is found, not just failed.
6. It runs in CI with the packaged launcher on every desktop runner, macOS included, non-blocking under the same
   ten-green-runs rule as the first smoke test, and locally on the Mac as part of the macOS engineer's pass.

**Notifications:**

- **(a) Approved: a system-notifier check now, but opt-in.** Not part of the plain `--self-test` (a user who runs
  the self-test must not get a notification): a separate switch, `--self-test=notify` or equivalent, that posts one
  fixed notification through the system notifier, time-limited, and reports whether the backend accepted it. CI uses
  it in the Linux smoke job on a real session bus; add Windows and macOS when their smoke jobs exist. The UI smoke
  step that goes through the real new-chapter path comes later, as you propose.
- **(b) Not intended.** The rule is the same on every system: "Show system notifications" governs notifications, the
  tray icon setting governs only the icon. If Windows cannot show a toast without a tray icon, the notifier adds a
  temporary icon for the life of the message and removes it afterwards when the tray setting is off. Report what the
  first smoke run shows, then build that; no further question needed unless Windows behaves differently from both
  expectations.
- **(c)** Correct the summary once (b) is settled, so the string changes once: it should then simply say the icon
  gives quick access from the system tray or menu bar, with no claim about notifications.

## ANSWER 012 — the three Android fix patches are in #2023 (2026-11-28)

Applied with `git am --3way` on `fix/android-bugs-found-by-multiplatform`, in your order of authorship (`MdList`,
library category filter, updater tag), and pushed: the pull request now has 15 commits, and its description lists
fixes 13 to 15 and your note on what fix 14 does under a category filter. The patch format worked as delivered; keep
it. The patch files stay in `docs/kmp-desktop/android-fixes/` until the maintainer has merged the pull request, then
remove them in the merge of `master` that brings the fixes back.

Go ahead with A4.

## ANSWER 013 — the lock on file systems without locking, and the signing jobs (2026-11-28)

Report read. The containment is what I asked for; the `release` job insisting on exactly the four packages is a good
addition.

**Question 1 — a data folder whose file system cannot lock: option (c), with the warning.** Do not refuse to start,
and do not run with nothing.
- When `tryLock` throws (as opposed to reporting the lock as taken), fall back to the port file as an advisory lock:
  if it exists and the instance it names answers the token on its loopback socket, another instance on this machine
  is running: signal it and exit as usual. If nothing answers, take over and write our own. That keeps the realistic
  case, two starts on one machine, protected.
- What this cannot see is a second computer opening the same shared folder. Say so once in the UI (a dismissible
  banner, remembered per data folder): this folder does not support locking, do not open it from two computers at
  the same time. Log it every start. New string in `i18n-kmk` base.
- Tests: `tryLock` throwing with a live instance (second start exits and signals), with a stale port file (takes
  over), and the banner shown once.

**Question 2 — the signing jobs: both approved.**
- (i) Import the macOS key with `-T /usr/bin/codesign` (add each further Apple tool the packaging actually calls,
  found from the Compose plugin's signing code, not guessed) and `security set-key-partition-list`, never `-A`. It
  cannot be verified without certificates, so: mark it in BUILDING.md "Signing" as untested, and add it to the
  checklist for the first signed build, with the exact error to expect if a tool is missing from the list and the
  one-line fix. A first signed build that fails loudly is acceptable; a key every process can use is not.
- (ii) Pin WiX by version and SHA-256. If the Compose plugin's own download cannot be verified, download and verify
  it in the workflow and point the plugin at that folder; report which of the two it became.
- Apply the same eye to everything else those jobs download and run: actions pinned by commit (they are), the JDK
  from `setup-java`, Gradle through the wrapper with its checksum, and `appimagetool` or similar if present. List
  what is pinned how in BUILDING.md, one table.

**The iOS WebKit tests.** IOS_CI.md, IOS_OPTIONS.md and my ANSWER 007 all rest on "25 web-view tests green on the
simulator". If WebKit never ran in CI, that sentence is wrong everywhere it appears. Establish it from the old run
logs, then correct the documents to what was actually proven (and what was only compiled), and tell me whether it
changes anything in the iOS options report's assessment of the web-view bridge. A test step that cannot fail is
worse than none; the fix to its exit status was the right call.

**The `.deb` and the Windows temp folders:** agreed as routed. For the Windows job, the fix is to close what is
open (the same idea as the Linux teardown work in PROGRESS.md "Review fixes (temp directory teardown)"), not to
relax clean-up.

## ANSWER 014 — A4, the `CloudflareInterceptor` hook, and landing the merge (2026-11-28)

**Keep the hook.** One `actual class … actual constructor(` line inside a KMK marker is the right price: the
alternative copies about 200 lines of `NetworkHelper` and would then silently miss what `master` adds to the HTTP
clients, which is a worse way to drift than one marked line. Add `CloudflareInterceptor.kt` to the short list of
borrowing hooks in UPSTREAM_AUDIT.md, next to `ArchiveReader`. The rule stays "byte-identical except the listed
hooks"; a hook is a single declaration-level line, never behaviour. If a future merge of `master` ever conflicts on
that line, that is the moment to reconsider.

**Landing plan: approved.** Land it when the Reviewer's findings on the five commits are fixed or recorded: merge
the branch into `multiplatform`, push, desktop build and iOS check. Then, in this order:
- a short entry in PROGRESS.md as COORDINATION.md "Merging master" asks, including the four desktop behaviour changes
  that came from following `master`, so nobody later takes them for regressions;
- the two parity gaps (no "MangaDex session expired" notification, no batch "reset info") go on the parity list in
  HANDOVER.md "Left to do"; they are priority 5, not now;
- the macOS on-device pass re-checks the areas the merge touched (library range selection, the filter panel,
  a Cloudflare-protected source, MDList) in the packaged app.

**`ImageUtilTest` "splits tall images".** A test that reads the real screen height of the machine it runs on is a
defect whether or not CI hides it: it fails on your Mac, so it will fail on the maintainer's. Make it deterministic
(inject the display height, or the headless property in the test task), in our desktop code or test set-up, not by
changing `master`'s test if it is `master`'s. If the cause is in `master`-owned code, it is an Android-fix patch
like the others.

The next merge of `master` is due when the maintainer merges #2023, or in two weeks, whichever comes first.

## ANSWER 015 — `HttpPageLoader` and `ChapterCache` (2026-11-28)

**1. `HttpPageLoader`: approved as proposed.** It is a real Android bug (a page marked ready before its image is in
the cache), so it goes the way of the others: deliver the patch, Atlas's team adds it to #2023 while that is open.
It stays on `multiplatform` in the meantime, which COORDINATION.md allows for exactly this case, on these terms:
- the block here and the patch are the same text, so the merge of `master` that brings it back needs no resolution;
- UPSTREAM_AUDIT.md lists it as a temporary difference with that exit;
- the patch carries a test if `master`'s test set-up can reach the loader; if it cannot, say so in the patch
  message together with how the race was reproduced (the 15-of-15 run under load), because the maintainer will ask.

**2. `ChapterCache`: keep the reflection, as you recommend,** with the test that fails loudly when the field is
renamed or removed, and with all of it in one desktop helper (one place to fix after a merge of `master`). A
`close()` on `master`'s class is not a bug fix and does not go into #2023. I will mention to the maintainer that a
small `close()` there would let desktop drop the reflection; that is his to want or not.

General rule from these two, for the whole team: **reflection into `master`-owned classes is allowed only for
shutdown and test teardown, only from desktop code, always behind a test that names the member.** Anything a running
app's behaviour would depend on needs a real seam, and that is a `QUESTION` to me.

The engineer who backed the `close()` change out under a rule that reached them late did the right thing.

## ANSWER 016 — the compat layer: `android.graphics` and the collator (2026-11-28)

The measurement is what I needed, and it changes the picture: sixteen in a hundred extensions install, list, and
then cannot show a page. That is a visible defect of the desktop app, not a parity nicety.

**Q2 — `android.graphics`, first group: approved, and it is the next thing the Extension runtime engineer builds.**
`Bitmap`, `Bitmap.CompressFormat`, `Bitmap.Config`, `Canvas`, `Paint`, `BitmapFactory`, `Rect`, as *real behaviour*
classes over Skia (Skiko is already a dependency; no new library). Conditions:
- **Scope is what the catalogue calls, not the Android API.** Start from the link check's list of the members those
  275 extensions actually reference on these classes; implement those; every other member throws
  `UnsupportedOnDesktop` with its name, so the next gap reports itself instead of drawing wrong.
- **Pixel fidelity is the whole point.** Descramblers move rectangles of pixels and expect them back bit for bit.
  Tests compare against known answers for: decode (JPEG, PNG, WebP, GIF first frame) to `ARGB_8888`;
  `getPixel` / `getPixels` / `setPixels` (unpremultiplied values, as Android returns them);
  `Canvas.drawBitmap` with source and destination rectangles, with and without scaling, with a null `Paint`;
  `Bitmap.createBitmap` (sub-rectangle, and with a matrix if referenced); `compress` to JPEG at a quality, PNG and
  WebP, re-decoded and compared within a stated tolerance for the lossy ones. Use three or four real scrambling
  schemes from extensions in the catalogue as end-to-end cases (their algorithm re-stated in the test, no
  extension code or third-party images committed; generate the test images).
- **Memory and threads:** a `Bitmap` owns native memory: `recycle()` frees it, a cleaner frees what an extension
  forgets, and nothing here touches the UI thread or assumes one. A page interceptor runs on OkHttp's threads, several
  at once.
- **Registry:** each class registered as *real behaviour* with the count from your table.
- **Done means seen:** at least two extensions that unscramble pages read a chapter correctly in the packaged app on
  the Mac, and the pinned real-extension CI job gains one such extension with a check on a decoded page (hash of a
  generated, locally served image run through the extension's interceptor if that can be done without the network;
  if it cannot, say so and keep it to the on-device check).
- `ColorMatrix` (the one extension blocked outright) rides along if it is small; otherwise it stays "needs …".
- Text drawing (`Typeface`, `StaticLayout`, 31 extensions) is not approved yet. After the first group is in, bring
  me what those 31 use it for.

**Q1 — `android.icu.text.RuleBasedCollator`: approved, low priority,** after the graphics group. About 60 lines
with numeric-ordering tests is in proportion for two extensions; do not grow it into ICU.

Also noted and good: the path checks, the registry with its test, the "needs …" state with an uninstall button, the
per-extension data folders, and the honest table. Update HANDOVER.md and PROGRESS.md to these numbers and retire
"1,683 of 1,686" (1,699 of 1,701 create their sources; 275 of them need graphics classes to show pages).

## ANSWER 017 — the graphics group is in; text drawing waits (2026-11-28)

Good work, and quickly. The loopback check through the extension's own interceptor is exactly the kind of test I
hoped was possible.

- **Text drawing: agreed, not now.** Put it on the parity list in HANDOVER.md "Left to do" with your breakdown
  (about 23 through Keiyoushi's text-interceptor library, 4 speech-bubble overlays, the rest captions; `thefarside`
  the one source broken as a whole) and the plan to start with the text-interceptor library. Until then, make the
  failure honest: a page that needs text drawing shows the reader's "unsupported" state naming what is missing
  ("this page is drawn as text, which the desktop app does not support yet"), not a generic load error or an endless
  retry. That is small; do it with the graphics group's review fixes.
- **`projectsuki`:** leave it at "needs …". No ICU.
- **The graphics group is not done until it has been seen in the packaged app** (ANSWER 016). I am telling the
  maintainer that the on-device pass is waiting for the Mac's screen to be unlocked. Until it is, keep the item open
  in each report as "seen headless, not in the packaged app".
- **The Windows jar lock:** good catch. Make sure the same close-and-delete runs on update, uninstall, a failed
  load and app exit, with a test that the jar is gone, and that a class loader still in use by a running download is
  not pulled out from under it (the old one is closed when its last source is released, or the update waits).
