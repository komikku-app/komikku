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
