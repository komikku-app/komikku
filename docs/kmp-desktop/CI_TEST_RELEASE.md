Status: Test and release engineer — done: run 46120905511 read, R.2 fixed (15/15) and sent to master as a patch (ANSWER 015), ANSWER 013 signing and pinning, Linux notification smoke, compat tests on Windows, Windows test teardown fixes, ChapterCache reflection in one helper behind a test, MangayomiSourceStoreTest flake fixed (20/20), Linux smoke without the menu folder workaround, macOS smoke design note — left: the next CI run (Windows tests, Linux notification and menu entry rows), the product bugs listed below (other owners)

# Desktop CI: test and release notes

The Test and release engineer's working file (INSTRUCTION 006): results of the desktop CI runs, open CI findings and
designs not built yet. Forge folds what matters into PROGRESS.md / HANDOVER.md.

## Run 46132077598 (`a997a4421c`: the Windows test fixes, before `0517ff5675`)

| Job | Result |
|---|---|
| Package (all four), real-extension self-test (all four) | passed |
| Smoke, Linux | passed, every row, including the notification rows: C (no service: exit 1, as planned), the plain self-test posting no `Notify`, A (`notify-send`) and B (`gdbus`) with the expected app name, title and escaped body on the bus, and the same body from both. Still with the old menu-folder workaround (`d776182683` is not in it) |
| Smoke, Windows | passed, every row |
| Desktop tests (windows-2022, non-blocking) | failed: 484 tests, **4 failed** (48 in run 46120905511), 1 skipped; `:desktop:android-compat:test` 60 tests, 0 failed. The 4 are the two product bugs below and nothing else: `DesktopSingleInstanceTest` busy dialog path (`C:"Users"…`), `DesktopExtensionManagerTest` "loads the installed extensions" (3 jars, 1 expected) and the teardown of it and `ExtensionsUiTest` (only `extensions/jars/*.jar` locked). Once those are fixed the job can become blocking |

- Menu folders on the Linux runner (`logs/menu-folders.log`): `/usr/local/share/applications` is in the image, and
  `/usr/share/applications` existed before the install (created during the job, apparently by the test tools'
  packages). So this runner never has "neither folder"; the original failure (run 46120905511) came from the missing
  `desktop-directories` folder, which postinst now handles. The new rows check the entry wherever it went.
- Both smoke logs still have 1 line (F1 not in this build); the "no error line" checks stay weak until it is.

## Run 46120905511 (`2ee84bfca1`, trigger run 14)

| Job | Result |
|---|---|
| Package: Linux, Windows, macOS ARM, macOS Intel | passed |
| Self-test with real extensions (`extension-check`), all four | passed: the 4 pinned APKs downloaded and verified by SHA-256, `SELF-TEST PASSED`; token `contents: read` only |
| Link check of the Keiyoushi catalogue (`extension-breadth`) | passed: 1699 extensions checked, 0 download failures, report in the artifact |
| Desktop app (compile + tests) (`pr-check`) | skipped, as it should be on a push: it runs on pull requests only, not seen running yet |
| Smoke, Windows | passed, every check, including the second start (exit code 0, window restored, no second window), the library compared with the start and More screens, the checksum count and the log allow-list (nothing needed it) |
| Smoke, Linux | failed at the install: the package's post-install script (`xdg-desktop-menu install`) exits with 3, "No writable system menu directory found"; fixed in the smoke script (below) |
| Desktop tests (windows-2022, non-blocking) | failed: 484 tests, 48 failed, 1 skipped (60 of 470 in run 46117892032) |
| Attach to release | skipped (not a release run); the artifact allow-list is not exercised before a release run |

- Linux smoke fix (`fd0807e357`): before the install the script creates `/usr/share/applications` when the runner lacks
  it (a desktop system has it) and records the folders and `sudo`'s `XDG_*` variables in `logs/menu-folders.log`. The
  existing "Menu entry registered" row then shows whether the post-install script registered the entry. A product note
  for the Windows and Linux engineer: jpackage's post-install script fails the whole install on a system without that
  folder (a minimal or headless system); low, since desktop systems have it.
- The real-extension job prints the self-test report twice (the launcher's stdout, then the report file, which the step
  prints because the Windows GUI launcher can drop stdout). Cosmetic, left as is.
- The Windows app log of the smoke run had one line (the single-instance hand-over); the automatic backup error of run
  46117892032 (R.1) did not appear. That proves nothing: a fresh start writes no log file (F1, Shared-code engineer),
  so the "no error line" checks pass on a nearly empty log. Check the line count again once F1 is in.

## R.2: `ReaderWidePagesUiTest` "rotate wide pages to fit" (fixed, `0a77c5bc21`)

- **Which wait:** the Linux test report of run 46117892032 (artifact `desktop-test-report-linux-X64-…`) has the full
  trace: line 207, the wait for the wide page (`page-2`) to be taller than wide. Everything before it took about 1.4 s,
  so the page was shown and stayed wrong for 15 s; slowness alone does not explain it. In the last 17 desktop runs it
  failed once (the other UI test failures were other tests, once each).
- **Not reproduced by load:** 10 runs of the class under 6 busy loops on this Mac, and runs with the wide page's
  download delayed by 0.3 / 1 / 3 s, all passed.
- **Cause (Android code, `HttpPageLoader`, also on `master`):** two reader threads (`eh_reader_threads`, default 2)
  take pages from one priority queue, and a page can be in it several times (every `loadPage` caller queues it: the
  paged viewer's preload of the neighbours and its shown slot, plus the `preloadNextPages` of earlier pages). A thread
  skips an entry only when the page is no longer `Queue`, and it marks the page as downloading only after a chapter
  cache lookup. Two threads that take the page within that window both download it. The chapter cache's editor goes
  to the first; the second one's `putImageToCache` gets no editor and returns without writing, and that thread sets
  the page's stream and `Ready` while the image is not in the cache. The desktop reader decodes on `Ready`, gets
  `FileNotFoundException` and shows the error placeholder; the first thread's later `Ready` changes nothing (same state),
  so the page never decodes again. Likelier on a cold JVM (first test of a fresh test JVM, first cache access), which
  is where it failed. Android's reader has the same path (decode on `Ready`).
- **Shown deterministically:** a scratch test holding the chapter cache's lock while two `loadPage` calls queue the
  same page: both threads download it (2 requests) and at the first `Ready` the image file does not exist
  (`FileNotFoundException …/chapter_disk_cache/<key>.0`), final state `Ready`.
- **Fix (`0a77c5bc21`, `HttpPageLoader`, KMK block):** a set of the pages a reader thread is loading now; a thread that
  takes a page another thread is loading skips that entry, and one that gets it after the other finished loads it only
  while it is still `Queue`. No test was loosened. `HttpPageLoaderRaceTest` (desktopApp) is the scratch test made
  permanent: it failed before the fix (2 image requests, `Ready` before the file existed) and passes after it.
- **Repeated runs** (`.git/forge/gradlew-locked`, JDK 21, a clean worktree of `HEAD` plus the fix, each run a fresh
  Gradle test JVM with `--rerun --max-workers=2`, two busy loops beside it): the whole reader test package
  (`app.komikku.desktop.reader.*`, 48 tests incl. `ReaderWidePagesUiTest` and `HttpPageLoaderRaceTest`) **15 of 15
  passed**. Gates on that tree: `:app:spotlessCheck`, `:desktopApp:spotlessCheck`, `:app:verifyBorrowedSources` passed.
- The same race exists on Android (`master`). ANSWER 015: it goes to `master` as
  `docs/kmp-desktop/android-fixes/httppageloader-race.patch` (for #2023), with a new unit test
  (`HttpPageLoaderTest`, failed before the fix, 5 of 5 after); it stays on `multiplatform` meanwhile, listed as a
  temporary difference in UPSTREAM_AUDIT.md 7.2.

## ANSWER 013 (signing and what CI downloads)

- (i) `8802da8132`: the macOS key is imported with `-T /usr/bin/codesign`, partition list `apple-tool:,apple:,codesign:`,
  never `-A`. From the Compose plugin 1.12.2 code: `MacSigner` signs with `/usr/bin/codesign` and only reads the
  certificate with `/usr/bin/security`; notarization (`xcrun notarytool`, `stapler`) and the DMG tools do not use the key.
  BUILDING.md "Signing" marks it untested and has the first-signed-build checklist (expected error if a tool is missing:
  `errSecInternalComponent` / `User interaction is not allowed.`; fix: one more `-T <path>`).
- (ii) `c2897cd5fe`: **the workflow downloads WiX and points the plugin at it.** The plugin's own `downloadWix` task
  fetches `wix311-binaries.zip` without any check and keeps it in the Gradle user home (which setup-gradle caches). The
  Windows `package` job now downloads WiX 3.11.2 (`wix3112rtm`), checks SHA-256
  `2c1888d5d1dba377fc7fa14444cf556963747ff9a0a289a3599cf09da03b9e2e` (computed here and found in 80+ public build
  scripts, e.g. PyOxidizer, Mozilla VPN), unpacks it and sets `WIX_PATH`, with which the plugin creates no download task.
  The step was run locally with PowerShell 7: correct hash passes and writes `WIX_PATH`; a wrong hash fails before
  unpacking. Not yet run on a Windows runner.
- (iii) `c2897cd5fe`: BUILDING.md "What CI downloads and runs" (one table). Also added `verify-signature: true` to the
  three `setup-java` steps (Adoptium's GPG signature when a JDK is downloaded). **Open, for a decision:** the Gradle
  distribution has no `distributionSha256Sum` (9.3.1: `b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`)
  and there is no `gradle/verification-metadata.xml`; both files are shared with the Android build on `master`.
- `f4f40f3fac`: BUILDING.md "Data directory" lists `instance.lock` and `instance.port` (Forge 14:41 (a)).

## Smoke and Windows test job additions

- `eda09f3d3f`: the Linux smoke job runs `--self-test-notify` as WINDOWS_LINUX_TEST_PLAN.md section 5 describes, after
  the window checks: run C without a notification service (exit 1 and a `FAIL system notification` line expected), then
  dunst installed, the plain self-test (exit 0, no `Notify` call on the bus), A (`notify-send`) and B (`=gdbus`) with
  exit 0 and the exact `OK … (accepted)` line, the `Notify` call's app name, title and escaped body pieces, and the same
  body from both. Each run on its own `dbus-run-session` with `dbus-monitor`; one difference from the plan's function:
  it also takes the screenshot inside the session, because dunst ends with that bus. Checked locally only with stubs
  (control flow and the monitor-log parsing); the first real run is the next CI run.
- `8d5972ae2f`: the `windows-test` job runs `:desktopApp:test :desktop:android-compat:test --continue` and uploads both
  modules' reports.

## Windows unit test failures (run 46120905511: 48 of 484)

| Group | Tests | Cause | Fix |
|---|---|---|---|
| Teardown: open database and chapter cache | 36 classes (`executionError`, `Failed to close extension context`) | `@TempDir` deletion: `databases/tachiyomi.db{,-shm,-wal}` and `cache/chapter_disk_cache/journal` still open (the Injekt graph's SQLite driver and `ChapterCache`'s `DiskLruCache`); Linux and macOS delete open files, Windows does not | `DesktopInjekt.onOpened` / `closeOpenFiles()`: the DI module registers the database driver, the network cache, the chapter cache (its private `diskCache` closed by reflection: `ChapterCache` is master's file and must stay identical; ANSWER 015: all of it in `ChapterCacheReflection`, behind `ChapterCacheReflectionTest`, `c3c4df0f7f`) and the Coil disk cache when they are created; `DesktopDataDirTeardown` closes them after stopping the writers. A location already deleted is skipped (`e10450b554`) |
| Teardown: network cache outside Injekt | `DesktopNetworkTest` (2), `DesktopWebViewTest` (2), `DesktopCloudflareInterceptorTest`, `FlareSolverrBackendTest` | their own `DesktopServices` / `NetworkHelper`: `network_cache/journal` open | the tests close `client.cache` after use (`3934443c74`) |
| Teardown: Coil disk cache | `JsSourceBrowseUiTest`, `ExtensionsUiTest` (with others) | `cache/image_cache/journal` open | registered in `KomikkuApp`'s image loader factory (`e10450b554`) |
| Child process log | `DesktopSingleInstanceTest` "two data folders run side by side" | the child JVM's stderr went to `child-0.log` in `@TempDir`, still locked at deletion | the child's stderr is read through a pipe into memory (and shown when an awaited line is missing) (`30f75ccd5b`) |
| Unclosed reader | `DownloadManagerTest` "pending deletion" (Forge 14:56 (c)) | test 1 read `ComicInfo.xml` with `reader().readText()` without closing it; Windows then cannot delete chapter 1 | `use { it.readText() }` (`30f75ccd5b`) |
| Report line ends | `DesktopSelfTestTest` | `\r\n` in stack traces | already fixed in `91d30f188f` (Windows and Linux engineer) |
| **Product bug** | `DesktopSingleInstanceTest` "does not answer within two seconds" | the busy dialog shows `C:"Users"…` : `DesktopLocalize.kt`'s `fixed()` (moko-resources workaround) replaces every `\` with `"` **after** formatting, so every argument with a backslash (every Windows path in a message) is mangled. Owner: Shared-code engineer. Fix idea: apply the workaround to the resource text before formatting, not to the arguments | not fixed |
| **Product bug** | `DesktopExtensionManagerTest` "a new manager loads the installed extensions from disk" (3 jars, 1 expected) and that class's / `ExtensionsUiTest`'s teardown (`extensions/jars/*.jar` open) | the class loaders of replaced or uninstalled extensions are never closed, so on Windows `pruneJarCache` cannot delete their converted jars: they pile up in `extensions/jars/` after each update. Owner: Extension runtime engineer. Fix idea: close an extension's `ExtensionClassLoader` when it is unloaded / replaced, before pruning | not fixed |

Also found: `NetworkHelper.clientBuilder()` (KMK, Android too) builds a new `okhttp3.Cache` on the same
`network_cache` folder for every client it builds (`client` and each `clientWithTimeOut()`); two `DiskLruCache`s on one
journal can corrupt it. Owner: Shared-code engineer; not changed here.

Local check (macOS cannot reproduce Windows locking): a scratch copy of `DesktopDataDirTeardown` listed, with `lsof`,
the files under the test data folders still open after the teardown. Before the fix: the database and journals as on
Windows; after (with the committed code, chapter cache closed by reflection): only the extension jars of the product
bug above. Gates and `:desktopApp:test` on that tree (`.git/forge/gradlew-locked`, JDK 21): Spotless, borrowed sources,
462 tests, 0 failed, 1 skipped.

**What remains red in `windows-test` until the two product bugs are fixed:** `DesktopSingleInstanceTest` "does not
answer within two seconds" (busy dialog path) and `DesktopExtensionManagerTest` (stale jars, plus its and
`ExtensionsUiTest`'s teardown on the open jars). Everything else in the run's 48 should pass; the next CI run shows it.

`DesktopNetworkTest` with other tests' cookies (Forge 14:41 (b)): the master merge already clears the shared cookie
store before each test in that class; left to the merge, not changed here.

## `MangayomiSourceStoreTest` "uninstall removes the source and its files" (fixed, `0517ff5675`)

- **Failure** (run 46129485116, Linux `package`, 1 of 477): Gradle's line 322 is the `runBlocking` call (coroutine
  stack trace recovery); the HTML report has the real line, 326: `store.sources.value.shouldBeEmpty()` right after
  `store.uninstall(ID)`, "List should be empty but has 1 elements, first being: Fixture JS".
- **Cause (test):** `MangayomiSourceStore.sources` is `combine(loaded, showNsfw).stateIn(scope)`: it follows a change
  of the loaded sources on the store's scope (`Dispatchers.IO`), after `uninstall` returned, not before. The test read
  `.value` at once; it usually passed because nothing else was running. Not the ordered tests sharing state: nothing
  else writes the loaded sources after the first load. Shown deterministically with a scratch test (not committed)
  whose store scope runs on one thread kept busy during `uninstall`: `installed` was already empty, `sources.value`
  still held the source, and it emptied once the thread was free. No product caller reads `sources.value` (the source
  managers collect the flow).
- **Fix:** the test waits for the expected list (`sourcesWhere`, at most 5 s, as the class already did after the
  first install and the NSFW switch) after install, update and uninstall; the same race was in three more reads (the
  update's new source, the NSFW update, the update after the storage failure). Nothing was relaxed: each check still
  requires the same list.
- **Repeated runs** (`.git/forge/gradlew-locked`, JDK 21, fresh test JVM each with `--rerun`, two busy loops beside
  it): **20 of 20 passed** (16 tests each). `:desktopApp:spotlessCheck` passed.

## `MigrateMangaUiTest` "migrate now moves the read state…" (fixed, `e8e46dd09e`)

- **Failure** (run 46129485116 attempt 2, Linux `package`): `IllegalArgumentException: Detected multithreaded access to
  SnapshotStateObserver` from `FlushCoroutineDispatcher.performRun` on `DefaultDispatcher-worker-1`; below it
  `MigrationListScreenModel.removeManga` (IO) → `updateMigrationProgress` → `navigateBack` → `Channel.send`, which
  resumed the composition's collector *unconfined* on that worker.
- **Cause (test harness):** the deprecated v1 `runDesktopComposeUiTest` runs on `UnconfinedTestDispatcher`, and the
  Compose test's `FlushCoroutineDispatcher` launches its frame work on that dispatcher, so a composition effect resumed
  from a worker runs (with the snapshot notifications) on the worker while layout runs on the AWT thread. Shown with a
  scratch probe (not committed): a `LaunchedEffect` receiving from a channel that an IO coroutine sends to ran on
  `DefaultDispatcher-worker-1/-3` with layout on `AWT-EventQueue-0` under v1, and both on `AWT-EventQueue-0` under v2.
  In the app the effect dispatcher is the Swing thread, so production code is not affected; nothing master-owned
  is at fault.
- **Fix:** the class uses `androidx.compose.ui.test.v2.runDesktopComposeUiTest` (same checks). **20 of 20** runs under
  load passed (10 tests each, fresh JVM, two busy loops), run in a clean worktree of `HEAD` because the main tree did
  not configure (another role's uncommitted `desktop/android-compat/build.gradle.kts`).
- **Same exposure elsewhere:** the other 46 UI test classes still use v1; any of them whose screen model resumes a
  composition from a worker can fail the same way, rarely. Proposal: move them to v2 one at a time with repeated runs,
  as a separate task.

## Breadth job reads the whole index (`5d7eaf656b`, Forge 16:58)

The job's own grep found 1,686 APKs; the packaged checker's `--index` finds all 1,701 at `37749703df` (checked locally
with `:desktop:extension-runtime:linkCheck --index=… --from=1695 --count=10`: `[1701/1701]`). It now downloads and
checks one APK at a time (the grep version downloaded 8 at once), so the job takes longer; its timeout is 90 minutes.
Progress goes to `breadth/progress.log` in the artifact.

## ANSWER 015 (master-owned files) and the Linux menu entry

- **`HttpPageLoader`:** `docs/kmp-desktop/android-fixes/httppageloader-race.patch` (README entry), made from local
  branch `fix/httppageloader-race` on `origin/master` `15610e67e4` in a temporary worktree: the file is
  byte-identical to `multiplatform`'s; new `HttpPageLoaderTest` (MockK chapter cache with one writer per key). Before
  the fix it failed (`Ready` with the image not cached), after it 5 of 5 passed; `spotlessApply`, `spotlessCheck`,
  `:app:compileDebugKotlin` passed there; `git am --3way` on a clean `origin/master` gives the same tree.
  UPSTREAM_AUDIT.md 7.2 lists it as a temporary difference.
- **`ChapterCache`:** `c3c4df0f7f`: `ChapterCacheReflection` is the only reflection into it (the teardown close and
  the reader race test's lock); `ChapterCacheReflectionTest` fails naming the field when master renames, removes or
  retypes it, and checks that the close really closes the disk cache. Note for Forge: the race test's use (holding the
  cache's lock) is test set-up, not teardown; it goes through the helper too.
- **Linux smoke (`d776182683`, Forge 16:47):** the workaround `fd0807e357` is gone; the job records the menu folders
  before the install and checks that `komikku-Komikku.desktop` is installed after it (in `/usr/share/applications`,
  where postinst copies it without menu folders, or `/usr/local/share/applications`, where `xdg-desktop-menu` puts it
  when that folder exists) and gone after the removal. Checked with `bash -n` only; the next CI run is the first real
  one.

## macOS smoke job (design only, not built)

What the Linux / Windows smoke covers, for `Komikku-<v>-macos-arm64.dmg` on `macos-15` (arm64), non-blocking like them:

1. Checksums of the artifact; `hdiutil attach -nobrowse -readonly` the DMG, `ditto` the `.app` to a temporary
   `Applications` folder, `hdiutil detach`. While unsigned: `xattr -dr com.apple.quarantine` is not needed (CI files
   have no quarantine flag); record `codesign -dv` and `spctl -a -vv` output as INFO (rejected until signing exists;
   with signing, a PASS check).
2. Start `Komikku.app/Contents/MacOS/Komikku` with `JAVA_TOOL_OPTIONS=-Dkomikku.dataDir=<temp>` (never the default
   `~/Library/Application Support/Komikku`, checked untouched at the end). GitHub's macOS runners have a logged-in
   GUI session, so no virtual display is needed.
3. Window: find it with `osascript -e 'tell application "System Events" to get name of windows of process "Komikku"'`
   (needs Accessibility for the runner's shell; the hosted image grants it to `/bin/bash`, to be confirmed in the first
   run); screenshots with `screencapture -l <window id>` (window id from a tiny Swift/`CGWindowListCopyWindowInfo`
   helper or `GetWindowID`) or the whole screen with `screencapture -x`.
4. Shortcuts: Cmd+1…5 (macOS uses Cmd, `DesktopShortcuts`) through `osascript … keystroke "2" using command down`,
   the same screenshot comparison as Linux (`compare` from ImageMagick via Homebrew, preinstalled on the image).
5. Second start: minimize (`set miniaturized of window 1` via System Events), start the launcher again on the same data
   folder: exit code 0, window un-minimized, one window.
6. Quit with Cmd+Q (the app's quit handler, `DesktopAppEvents`): process ends, exit code 0; the log check with the same
   allow-list; no `hs_err_pid*.log`.
7. `--self-test-notify` once (osascript backend; exit 0 and the `OK … osascript exited with 0` line), INFO only, as
   macOS does not say whether a banner appeared.
8. Upload screenshots and logs like the others. Open questions for the first run: Accessibility permission on the
   hosted runner, and whether an unsigned app started from the shell gets a window in front.
