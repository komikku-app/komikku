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
