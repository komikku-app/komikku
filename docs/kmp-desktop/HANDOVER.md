# Komikku Desktop / JS Sources — Handover

State as of 2026-11-23, branch `claude/kmp-multiplatform-support` (the I2b review and its fixes are done; see
"Done" and "Left to do").
Read with [`PLAN.md`](PLAN.md) (plan and decisions), [`PROGRESS.md`](PROGRESS.md) (step log,
deviations, moved files, compat classes), [`BUILDING.md`](BUILDING.md) (how to run/test/package),
[`MANGAYOMI_CONTRACT.md`](MANGAYOMI_CONTRACT.md) and [`MANGAYOMI_SURVEY.md`](MANGAYOMI_SURVEY.md).

## Working agreement (from the maintainer)

- Coordinator (planning, task instructions, code review) runs on Opus at max effort; coding is
  delegated to sub-agents on Opus at high effort. Earlier the coordinator was Fable 5.2 medium.
- After each coding step: review it (`/code-review max <range>`), then have a sub-agent fix the
  findings, then re-report the findings with outcomes.
- Commit and push after every step (and in small increments) so work survives usage-limit stops.
  Push only to `claude/kmp-multiplatform-support`. No Claude attribution in commits. No PR unless asked.
- Verification is compile/unit-test only: `spotlessApply` → `spotlessCheck`, module compile and test
  tasks, `:app:compileDebugKotlin`. No `assemble*`, no emulator, no `:desktopApp:run` until the
  maintainer asks. Use `--max-workers=2` (memory is tight) and `./gradlew --stop` between heavy runs.
- Don't ask for approval unless something would break the app; decide and record decisions here or in
  PROGRESS.md.
- When the usage limit hits, background agents keep their progress: resume them with SendMessage after
  the reset instead of starting new ones.

## Done

### Desktop app (Phases 0–9 of PLAN.md)
- PR #1671 rebased on master; Compose Multiplatform 1.12.2; `mihon.library.kmp` convention plugin
  (path-preserving KMP: `src/main` → `jvmSharedMain`).
- `core:common`, `source-api` (extension ABI unchanged, verified with `javap`), `core-metadata`,
  `core:archive`, `domain`, `data`, `source-local` compile for Android + desktop.
- `:desktop:android-compat` (Android FQN shim), `:desktop:compose-compat`, `:desktop:extension-runtime`
  (APK parsing, signatures, dex2jar, class loader, `linkCheck` task), `:app-shared` (source borrowing
  from `app` via `app-shared/borrowed-app-sources.gradle.kts`, checked by `:app:verifyBorrowedSources`).
- `:desktopApp`: library (Android's search/filter/sort/display), manga details, updates, history,
  browse + extensions, reader (paged + webtoon), downloads, backup, trackers, settings; packaging
  config and `build_desktop.yml`. Never launched; workflows never executed.
- Shared Android screens (Phase 6) deferred until the upstream ViewModel/Metro sync (PLAN "Gate outcome").

### iOS plan / JavaScript sources (PLAN.md section 8)
- **I0** contract + survey done (114 JS manga entries from 18 files; Apache-2.0).
- **I1/I2** `:source-js` (KMP host for Mangayomi JS extensions on `quickjs-kt` 1.0.15): engine,
  prelude, bridges, typed results, conformance tests. Reviewed at max level (15 findings) and all
  fixed in `cbe98eae24`, `2547914b21`, `195493a0f5`, `b5f7923dd7` (error envelope, hidden bindings,
  prototype lockdown, parity fixes, bytecode cache, `JsExtensionHost.extension(source, code)` →
  `JsExtension` with `metadata()`, CI runs `:source-js:desktopTest`).
- **I2b** (maintainer decision: switch Android to quickjs-kt): new `:core:quickjs` with a
  binary-compatible `app.cash.quickjs.QuickJs` (engine thread per instance, `set`/`get`, finalizer);
  old `quickjs-android` AAR removed; ProGuard keeps added; `:source-js` has an Android target; Android
  unit tests swap in quickjs-kt's JVM artifact. Commits `4f0c9489c3` … `712fa9294c`.

### Security decision on record
quickjs-kt (1.0.15 and 1.0.18) turns a JS `Error` reaching the JVM into
`NewObject(FindClass(error.name), (String) ctor)` — script code can construct arbitrary classes
(proved: `FileOutputStream` creates/truncates files). `:source-js` closes the paths through its own
code; unhandled rejections and the `app.cash.quickjs` API stay exposed. The maintainer accepted this
and chose to switch anyway (PLAN 8.7 item 5). Fix = one native check
(`IsAssignableFrom(cls, java/lang/Throwable)` in `js_error_to_java_error`); disabled regression tests
mark the residual path in both modules.

### Review of `b019b0e402..712fa9294c` (I1/I2 review fixes + I2b) — done
- Max-level review (finders, runtime verification, gap sweep): 2 known + ~60 candidates; fixed in the commits
  after `cab5b6d` (each `fix(kmp)` / `test(kmp)` / `refactor(kmp)` / `ci(kmp)` / `docs(kmp)` commit names its
  theme). What was fixed, what is documented as a deviation and what is left: PROGRESS.md "Review fixes (I2b)".
- Highlights: `QuickJs` completion values are converted in JavaScript (objects no longer throw or run getters; no
  JVM crash from completion values), `close()` ends stuck calls, quickjs-kt engine create/close serialised
  process-wide with a sentinel (the concurrent-create JVM crash is gone under stress and `-Xcheck:jni`), proxies
  keep their instance alive, idle engine threads end; `:source-js` wrapper escape closed, only
  `JsSourceException`s leave a call, settled calls return, host promises handled, HTTP body cap tied to the heap,
  adapter API (`NotImplemented`, `sourceId`, tolerant `metadata()`, `search(null)` in one context).
- New residual risks for the maintainer (PLAN 8.7 item 5): a page script can still crash the app through
  quickjs-kt's non-`Throwable` `Throw` (thrown values / rejections with throwing getters); a per-create JNI
  reference race remains; QuickJS out-of-memory crashed the JVM depending on code shape (guarded by
  `OutOfMemoryCrashTest`, cause unknown).
- The light Android checks `:app:checkDebugDuplicateClasses` and `:app:mergeDebugJavaResource` pass.

## Left to do (in order)

1. Re-report the review's findings with their outcomes (coordinator), then decide on the residual risks above.
2. **I3** source adapter: wrap `JsExtension` as `CatalogueSource`/`ConfigurableSource` (ids from the index
   id + fixed namespace, filters and preferences mapped both ways, cached `metadata()`, per-page headers,
   relative covers against `baseUrl`), Android + desktop. Review + fix.
3. **I4** store + UI: Mangayomi repository type (index fetch, install/update/uninstall, SHA-256 at
   install, NSFW flag, notes banner, `i18n-kmk` strings), registered in `DesktopSourceManager` and
   `AndroidSourceManager`. Review + fix.
4. I5–I7 (iOS core, iOS app, macOS CI) need a macOS host; re-check the JS catalogue size first (PLAN 8.9).
5. Desktop parity gaps (PROGRESS list): global search, source/language toggles, library grouping/batch
   actions, scheduled updates/backups, delegated sources, AVIF/JXL, WebView/Cloudflare, signing, updater.
6. Optional: report the quickjs-kt bugs upstream (draft in `QUICKJS_KT_SECURITY_REPORT.md`, with the crash and
   the JNI state race added; the maintainer submits it).
7. First real run by the maintainer: `./gradlew :desktopApp:run`, Android `assembleDebug` on a device
   (QuickJS switch is unverified on device and under R8).
