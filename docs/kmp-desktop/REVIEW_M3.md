# M3 review findings (the Mac session's M3 range)

Code review, track B of INSTRUCTION 001 (ATLAS_INSTRUCTIONS.md), 2026-11-28. Reviewer role; the owning roles fix.
Line numbers are those at `33443c0` unless stated otherwise.

## Reviewed range

`debe34e..33443c0`, 26 commits. None of them is in the already reviewed `cbdfdff..365f98a`: `365f98a` is an
ancestor of `debe34e`, so the two ranges do not overlap. The merge `93e827c` brings the five M2 review fix commits
(`57023979ac` … `4db89c95f1`), which `cbdfdff..365f98a` does not contain, so they are reviewed here.

| Commit | Subject |
|--------|---------|
| `71db09218f` | fix(kmp): Make converted APK extension classes pass the JVM verifier |
| `6249a1ac6c` | feat(kmp): Verify every converted extension class in the self-test |
| `40be410575` | feat(kmp): Show why an extension install failed |
| `baafcd17a4` | ci(kmp): Self-test the packaged app with real Keiyoushi extensions |
| `9284998fbe` | feat(kmp): Add android.util.LruCache to the compat layer |
| `be27aa9466` | fix(kmp): Create real extensions in the self-test with the app's graph |
| `2a62650e40` | fix(kmp): Convert names, inlined superclass constructors and resources of real extensions |
| `b6122ab4a5` | feat(kmp): Add Activity, activity lifecycle callbacks and Bundle to the compat layer |
| `a5bff9973f` | fix(kmp): Provide okhttp-zstd, AppInfo and http.agent to extensions |
| `d7bd1fae98` | test(kmp): Fail the self-test on host library gaps, check more real extensions |
| `465d7507ba` | feat(kmp): Add ApplicationInfo and Context.getApplicationInfo to the compat layer |
| `80d70c97dc` | fix(kmp): Run the bounded shutdown on Cmd+Q and window close, lock on Cmd+H |
| `50a572f384` | docs(kmp): Record M3, real APK extensions and the macOS items on the Mac |
| `8ead2d9952` | ci(kmp): Start a desktop build for M3 |
| `92ec56e434` | docs(kmp): Record the M3 checks through the packaged app's UI |
| `57023979ac` | fix(kmp): Keep dash titles out of osascript options, add a system notifications switch (via merge) |
| `81c414ff2a` | fix(kmp): Show one file dialog at a time (via merge) |
| `18141a2e90` | fix(kmp): Write failed checks' stack traces to the self-test report (via merge) |
| `3a160515f7` | docs(kmp): Record the M2 review fixes and the open folder panel finding (via merge) |
| `4db89c95f1` | ci(kmp): Start a desktop build for the M2 review fixes (via merge) |
| `653ca54a24` | test(kmp): Wait for the whole saved cover file in MangaCoverActionsUiTest |
| `8081977b08` | docs(kmp): Record the CI result of the M3 build and the Linux test race |
| `2510af1e8d` | ci(kmp): Start a desktop build for the cover test fix |
| `93e827c3b0` | Merge the M2 review fixes into the M3 work on the Mac |
| `ece6f92ef5` | Desktop docs: folder panel on lock confirmed and accepted, osascript marker checked on the Mac |
| `33443c0cab` | Desktop CI: bump the build trigger (run 12) |

## Summary

| Severity | Count | Findings |
|----------|-------|----------|
| High | 1 | 1 |
| Medium | 3 | 2, 3, 4 |
| Low | 7 | 5 – 11 |
| Design-level → Atlas | 3 | D1, D2, D3 |

Two findings were confirmed with a scratch test in `:desktop:extension-runtime` (run through the Gradle lock
wrapper, then deleted; nothing committed): 2 and 3.

## Findings

### 1. High: CI runs unpinned third-party extension code in the job that holds the signing identities

- **File, line:** `.github/workflows/build_desktop.yml`, step "Self-test the packaged app" (lines 304–338), in the
  `package` job after "Import macOS signing certificate" (lines 248–264) and before the Windows signing step
  (lines 341–360) and the artifact uploads (lines 374–390).
- **Problem:** the step downloads the current Hiperdex, Kagane, MangaDex and Weeb Central APKs from Keiyoushi's live
  index (`index.pb` from the `repo` branch, then whatever release URL it names) with no pin or checksum, and the
  packaged app's `--self-test-extensions` creates their sources, which runs their constructors and static
  initialisers (`ExtensionRuntime.instantiateSources`). That is third-party code executing on the runner of the job
  that, on a release run, has:
  - the macOS signing key imported with `security import … -A` (any application may use it without a prompt) into an
    unlocked keychain whose partition list allows `apple-tool:` / `apple:`;
  - the Windows `.pfx` and its password written and used in a later step of the same job (a process left behind by the
    extension code, or a `PATH` / `GITHUB_ENV` change it writes, would see or replace `signtool`);
  - the installers it later signs and uploads as release assets.

  Whoever can change Keiyoushi's `repo` branch or release assets (or a compromised extension build there) can thereby
  reach Komikku's signing identities and the release artifacts. The extension sandbox does not help here: the
  desktop runtime has no security manager, extensions run with the app's full rights.
  Secondary: the step's network dependency on GitHub and on a third party's index format, and on whatever those four
  extensions currently need, can fail a tag's `package` job (and so its `release` job, `needs: package`) for reasons
  unrelated to the commit (a new extension version that uses a Kotlin or OkHttp API the host lacks is a legitimate
  early warning, but it should not block a release).
- **Proposed fix:** move the real-extension check out of `package` into a separate job with no secrets
  (`permissions: contents: read`, no signing env), which downloads the packaged app artifact and runs it; pin the
  APKs (URL plus SHA-256, updated by hand or by a scheduled job that opens a PR) or commit them as test fixtures; keep
  only the built-in fixture APK in the signing job. Optionally make the live-index variant a scheduled, non-blocking
  workflow. Independently, `-A` on the macOS import could become `-T /usr/bin/codesign -T /usr/bin/productbuild`.
- **Owner:** Test and release engineer. **Design-level:** no.

### 2. Medium: an R8-inlined `new C()` inside one of `C`'s own constructors is not retargeted, the class fails verification

- **File, line:** `desktop/extension-runtime/src/main/kotlin/app/komikku/desktop/extension/dex/DexTranslator.kt`,
  lines 193–209 (`InlinedConstructorCalls.transform`).
- **Problem:** `newType` gives the class of a receiver for both a fresh `new T` and `this` (`THIS_REF` in a
  constructor → `method.owner`). The skip rule `receiver == method.owner && constructor && invoke.owner == superclass`
  is meant for the constructor's own `super(…)` call, but it also matches a `new C` created inside `C.<init>` whose
  inlined constructor body ends in `Base.<init>` (R8 inlined `C()` into the allocation site). That call is left as
  `invokespecial Base.<init>` on an uninitialised `C`, which the verifier rejects (`Bad <init> method call`), so the
  whole class fails to load. Typical shapes: a class building a default / sentinel instance of itself in its
  constructor, a node class creating its first child.
- **Evidence:** scratch test (deleted): an ASM-generated class `C extends Base` whose `<init>(I)V` calls
  `Base.<init>` on `this` and then does `new C; dup; invokespecial Base.<init>` (the R8 shape), dexed with D8 and
  converted by `DexJarConverter`: the converted class is reported unverifiable and loading it throws `VerifyError`.
  The catalogue sweep (1,683 / 1,686) did not hit the shape, so it is rare, but it silently breaks the whole extension
  when it occurs.
- **Proposed fix:** track the origin, not only the type: let `newType` return whether the value comes from `THIS_REF`
  or from a `NEW`, and skip only receivers that come from `THIS_REF`. Add the shape above to `DexPipelineTest`.
- **Owner:** macOS engineer. **Design-level:** no.

### 3. Medium: an APK's manifest package name is used in file paths unchecked (writes outside the extensions folder)

- **File, line:** `desktop/extension-runtime/src/main/kotlin/app/komikku/desktop/extension/ExtensionPackage.kt`
  line 53 (`pkgName = manifest.packageName`, no validation);
  `desktopApp/src/main/kotlin/app/komikku/desktop/extensions/DesktopExtensionManager.kt` lines 410 and 426
  (`File(apkDir, "${extension.pkgName}.apk")` and `.apk.tmp`), 480 (`uninstall`: `File(apkDir, "$pkgName.apk").delete()`),
  496.
- **Problem:** Android's package manager only accepts dotted Java-identifier package names; the desktop runtime
  accepts any string from the binary manifest. An extension APK installed from a file (`installApkFile` without
  `expectedPkgName`) with `package="../../x"` is copied to `<apkDir>/../../x.apk` (and its `.tmp`), i.e. anywhere the
  user can write, overwriting an existing `.apk` there; uninstalling it deletes that path. The repository install
  path happens to be stopped earlier by `File.createTempFile` (the JDK refuses a prefix with a separator), which is
  luck, not a check. Extensions are untrusted third-party files, so path components from them must be validated.
- **Evidence:** scratch test (deleted): an `ExtensionPackage.from` of an APK fixture whose manifest package is
  `../../evil` is `Valid`, and `File(apkDir, "${pkgName}.apk").canonicalFile` resolves outside `apkDir`
  (to `evil.apk` two levels up).
- **Proposed fix:** in `ExtensionPackage.from`, reject a package name that does not match Android's grammar
  (`[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+`); in the manager, additionally check that the canonical target is
  inside `apkDir` before copying or deleting. Add a test with a traversal name.
- **Owner:** macOS engineer (runtime and manager). **Design-level:** no.

### 4. Medium: desktop `AppInfo.getSupportedImageMimeTypes()` advertises formats the desktop cannot decode

- **File, line:** `app/src/main/java/eu/kanade/tachiyomi/AppInfo.kt` line 32 (borrowed as a desktop copy in
  `a5bff9973f`), with `core/common/src/desktopMain/kotlin/tachiyomi/core/common/util/system/ImageUtil.kt`
  lines 146–154 (`ImageType` lists AVIF, HEIF, JXL).
- **Problem:** the borrowed `AppInfo` returns every `ImageUtil.ImageType` MIME type, and the desktop `ImageType` keeps
  AVIF / HEIF / JPEG XL for detection. The desktop reader cannot decode them (PROGRESS.md 7.2 and P5: Skia on desktop
  has no AVIF / JXL / HEIF decoder, the page shows an "unsupported format" placeholder). Extensions call this method
  to build their `Accept` header or choose an image variant; on desktop they are told AVIF / JXL work, so a server
  that honours it sends pages the reader shows as errors, where Android shows them. A behaviour difference with no
  reason.
- **Proposed fix:** a desktop `AppInfo` (or a desktop-only source of the list) that returns only what the desktop
  decodes (`image/jpeg`, `image/png`, `image/gif`, `image/webp`, plus `image/bmp` if wanted), with a test; keep
  Android's file unchanged. Revisit when AVIF / JXL decoding lands.
- **Owner:** Shared-code engineer (borrowing) with the macOS engineer (who added it). **Design-level:** no.

### 5. Low: a failure in the bounded shutdown leaves the OS's quit request unanswered

- **File, line:** `desktopApp/src/main/kotlin/app/komikku/desktop/Main.kt` lines 109–111.
- **Problem:** `runBlocking { stopDesktopWork(…) }`, then `DesktopAppEvents.finishQuit()`, then `exitProcess(0)`, with
  no `try` / `finally`. `stopDesktopWork` bounds time, not errors: an exception from `scheduler.stop()`,
  `backupJobs.cancelAndJoin()`, the library runner or `Injekt.get()` skips `finishQuit()` (the pending
  `QuitResponse` is never answered, so a logout / restart waits for macOS's own timeout and reports the app as
  blocking it) and `exitProcess(0)` (the JVM then only ends if no non-daemon thread remains).
- **Proposed fix:** `try { runBlocking { … } } catch (e: Exception) { logcat … } finally { singleInstance / finishQuit / exitProcess }`.
- **Owner:** macOS engineer. **Design-level:** no.

### 6. Low: a second Cmd+Q during the shutdown bypasses it

- **File, line:** `desktopApp/src/main/kotlin/app/komikku/desktop/Main.kt` line 104 (`onDispose { … removeQuitHandler() }`),
  `desktopApp/src/main/kotlin/app/komikku/desktop/platform/DesktopAppEvents.kt` line 35.
- **Problem:** the quit handler is removed as soon as the `application` composition is disposed, i.e. before the up to
  10 s of `stopDesktopWork`. A second Cmd+Q / Dock → Quit in that window gets AWT's default handler, which exits the
  JVM at once, cutting off the backup the bounded shutdown is waiting for (the case the change exists for).
- **Proposed fix:** keep the handler installed until `finishQuit()` (remove it there, or let the handler answer
  repeated requests by keeping only the newest response, as `onQuitRequest` already does).
- **Owner:** macOS engineer. **Design-level:** no.

### 7. Low: hiding the app after minimising it restarts the idle lock timer

- **File, line:** `desktopApp/src/main/kotlin/app/komikku/desktop/security/DesktopAppLock.kt` lines 119–122.
- **Problem:** `onWindowHidden` (minimise and, since `80d70c97dc`, Cmd+H) writes `lastAppClosed = now` on every call.
  Minimise at t = 0, Cmd+H at t = 9 min with "lock after 10 min": the lock now applies 10 min after t = 9 min. On
  Android `onStop` fires once per trip to the background.
- **Proposed fix:** `if (hidden) return` after the enabled / locked check.
- **Owner:** macOS engineer. **Design-level:** no.

### 8. Low: the frame computation resolves classes in a different order than the extension class loader

- **File, line:** `desktop/extension-runtime/src/main/kotlin/app/komikku/desktop/extension/dex/StackMapFrames.kt`
  line 47 vs `desktop/extension-runtime/src/main/kotlin/app/komikku/desktop/extension/loader/ExtensionClassLoader.kt`
  lines 37–38.
- **Problem:** `StackMapFrames` looks a class up in the extension's own classes first, then on the host class path;
  the loader resolves the platform first, then shared packages (compat, `source-api`, OkHttp, …) from the host, then
  the jar. An extension that bundles its own copy of a shared-package class (an `okhttp3` or `android.*` class with
  a different hierarchy) gets frames computed against its copy and runs against the host's. The verifier still
  guards the result (no safety issue), but the class then fails to verify for a reason the self-test reports badly.
- **Proposed fix:** mirror the loader's order in `StackMapFrames.info` (platform / shared prefixes → host, else jar,
  else host), sharing the prefix list with `ExtensionClassLoader`.
- **Owner:** macOS engineer. **Design-level:** no.

### 9. Low: the system tray summary string changed meaning under the same key

- **File, line:** `i18n-kmk/src/commonMain/moko-resources/base/strings.xml` line 366 (`desktop_tray_icon_summary`,
  changed in `57023979ac`).
- **Problem:** the base text now says system notifications appear through the tray on Windows and Linux; the key was
  kept, so Weblate keeps the old translations as valid and non-English users read the old meaning until translators
  notice. (The base file is shared with `master`'s `i18n-kmk`, so the change will also show up in the merge.)
- **Proposed fix:** a new key for the new meaning (and remove the old one), so Weblate marks it untranslated.
- **Owner:** Shared-code engineer. **Design-level:** no.

### 10. Low: the AppleScript unmarking of dash titles is only checked as text

- **File, line:** `desktopApp/src/test/kotlin/app/komikku/desktop/notification/MacOsNotifierTest.kt`
  (from `57023979ac`).
- **Problem:** the fix prefixes titles that start with `-` with a marker and strips it in AppleScript
  (`text 2 thru -1`). The tests assert the script text and the argument list, but never run the script, so the
  unmarking itself (the part that broke before) is only covered by the manual Mac check recorded in `ece6f92ef5`.
- **Proposed fix:** a macOS-only test (`assumeTrue(isMac)`) that runs `osascript` with the generated script, with
  `display notification` replaced by `return` of the unmarked title, and asserts the output for `-x`, `--`, the marker
  alone and a normal title.
- **Owner:** Test and release engineer. **Design-level:** no.

### 11. Low: the install-failure reason is never cleared on uninstall

- **File, line:** `desktopApp/src/main/kotlin/app/komikku/desktop/extensions/DesktopExtensionManager.kt` lines 228–231
  (`installErrors`), 479 (`uninstall`).
- **Problem:** a failure reason stays in `installErrors` until a later successful install of the same package; after an
  uninstall (or when the package disappears from the repository) the stale reason can still be shown for an entry
  with the same package name. Cosmetic.
- **Proposed fix:** `installErrors.remove(pkgName)` in `uninstall` and when the available list is refreshed.
- **Owner:** macOS engineer. **Design-level:** no.

## Design-level → Atlas

### D1. Growth of the Android compat layer

- **Where it stands:** `:desktop:android-compat` has 78 Kotlin files under `android.*`. M3 added `LruCache` (a full
  reimplementation, behaviour equal to Android's: synchronized, `create` / `entryRemoved` / `sizeOf`, null checks),
  `Activity` (a `ContextWrapper` with a null base), `Application.ActivityLifecycleCallbacks` (registered, never
  called), `Bundle` (a typed map), `ApplicationInfo` + `Context.applicationInfo` (`dataDir`, `packageName`). Each was
  added because one or more real extensions referenced it at class creation (Kagane; HentaiCube, Emperor Scan,
  LxHentai; TCB Scans).
- **Where it is heading:** the sweep criterion is "the extension's sources can be created", so every class an
  extension touches in a constructor, field type or verifier-visible position gets a stand-in, whatever its
  semantics. Stand-ins of lifecycle types that are never driven (`Activity`, lifecycle callbacks) make an extension
  load but leave the feature dead silently; stand-ins with partial behaviour (`Bundle`, `ApplicationInfo`) must stay
  binary compatible with `android.jar` signatures forever, because extensions compile against Android's API.
  Without a rule the layer becomes an ad-hoc Android re-implementation that grows with each Keiyoushi release, with
  no test of behaviour beyond "does not throw".
- **Risks:** (1) semantic drift (an extension that relies on the lifecycle or `Bundle` contents works on Android and
  fails quietly on desktop, harder to diagnose than a load error); (2) maintenance and review load proportional to the
  catalogue; (3) security surface: everything in `android.*` is a shared package resolved from the host, so any compat
  class with real effects (files, network, processes) is reachable by all extensions; `ApplicationInfo.dataDir` hands
  them the app's data folder.
- **Options:** (a) an allow-list policy: a compat class is added only with the extension(s) needing it, the members
  they use, and a rule "type-only / no-op / real behaviour" recorded in PROGRESS's compat table, with a cap (for
  example no new real-behaviour class without an Atlas decision); (b) a periodic sweep in CI (scheduled, not in the
  signing job, see finding 1) that lists the `android.*` references of the catalogue, so additions are driven by data
  and removals are possible; (c) where only creation matters, a generated no-op stub layer (types and members as in
  `android.jar`, bodies throwing `UnsupportedOperationException` with the member name) instead of hand-written
  stand-ins, which makes gaps loud at run time instead of silent; (d) for lifecycle types, decide explicitly that they
  are never driven and document that extensions depending on them are unsupported.
- **Owned by:** Atlas (decision); macOS engineer / Shared-code engineer (implementation).

### D2. The three remaining extensions, and the real size of the `android.graphics` gap

- **What they need:** `aquellosquetraducen`: `android.icu.text.Collator` (locale-aware string comparison, probably for
  sorting); `tenkaiscan`: `android.graphics.ColorMatrix` (image colour transform, which implies `Bitmap`, `Canvas`,
  `Paint`, `ColorMatrixColorFilter` on the same path); `mehgazone`: `android.text.Spannable` (text spans; the compat
  layer has `Spanned`, `SpannableStringBuilder`, `Editable`, not `Spannable`).
- **The sweep understates the gap:** the sweep's pass criterion (convert, verify, create sources) and the self-test
  (`DesktopSelfTest.checkExtension`, lines 217–230: missing `android/` classes and members are listed, not failed,
  outside strict mode) only fail on classes needed to verify or create. An extension that uses
  `BitmapFactory` / `Bitmap` / `Canvas` inside an image interceptor (descrambling, stitching) verifies and creates fine
  and fails with `NoClassDefFoundError` when the first page is read. PROGRESS.md (section 5.3 and the link-check notes)
  already expects `android.graphics.Bitmap*` / `Canvas` as gaps. So "3 left" is the number that fail at load, not the
  number that cannot read; a link-check pass over the catalogue counting extensions with any missing `android.graphics`
  reference would give the real number.
- **Options and costs:** (a) `Collator` and `Spannable`: cheap (`java.text.Collator` / ICU4J behind the same
  signatures; `Spannable` as an interface implemented by the existing `SpannableStringBuilder`), low risk, two
  extensions; (b) `android.graphics` imaging subset (`Bitmap`, `BitmapFactory`, `Canvas`, `Paint`, `Rect`,
  `ColorMatrix`): medium to large; backed by Skia (already in the app through Compose / Skiko) or by `java.awt.image`;
  needs exact pixel semantics (premultiplied alpha, config, compress to JPEG / PNG / WebP) to be useful, plus tests per
  operation; benefits every descrambling extension, not only `tenkaiscan`; (c) accept and surface: keep them
  unsupported and show "this extension needs Android image APIs" in the Extensions tab (from the link check at
  install), cost small, honest to users.
  Suggested order: measure (link check over the catalogue), then (a) and (c), and decide (b) on the measured count.
- **Owned by:** Atlas (decision); macOS engineer (implementation).

### D3. Real Keiyoushi extensions as a release gate

Finding 1 is the security side; the design question is whether the release pipeline should depend on a third
party's current catalogue at all. Options: pinned fixtures in the repository (deterministic, updated deliberately),
pinned URLs plus hashes (no binaries in git, still deterministic), or a scheduled non-blocking job against the live
index that opens an issue on breakage. Atlas to choose; Test and release engineer implements.

## Checked and found fine

- **Stack map frames** (`StackMapFrames`): `COMPUTE_FRAMES` with `getCommonSuperClass` read from class files (jar
  classes, then host resources), never loading or initialising extension classes; interfaces and arrays merge to
  `Object` as ASM's default; a class whose frames cannot be computed is left as it is and reported
  (`unverifiableClasses`), and the verifier stays the guard.
- **Retargeted inlined constructors** (`InlinedConstructors`): synthetic marked constructors chain through each
  intermediate class to the real superclass constructor; the marker descriptor cannot collide with a real constructor
  in practice; only extension classes are changed; host classes as receivers are reported, not rewritten. Apart from
  finding 2, the `this` / `new` / local copy handling is correct.
- **Member names with `-`** (Kotlin value-class mangling such as `getZERO-UwyO8pc`): kept as is, valid JVM names.
- **Resource copying:** APK entries are streamed into the jar (no extraction to disk, so no zip-slip); dex, class,
  signature and manifest entries are skipped; resource lookup is platform → jar → parent, so APK resources only shadow
  what extension classes look up, as on Android's `PathClassLoader`; conversion errors of any type become
  `ExtensionLoadException`.
- **Class loading:** platform first, shared packages parent-first, so an extension cannot replace host or compat
  classes; cache file names include the APK's SHA-256 and a converter version; `prune` removes stale jars.
- **Host additions:** `okhttp-zstd` comes from the shared OkHttp bundle (the zstd self-test decodes a real frame
  through the native library); `http.agent` is set only when unset and before Injekt starts (OS name / version / arch,
  as Android's default agent exposes); `AppInfo` is borrowed through `desktopCopy` and covered by `verifyBorrowedSources`.
- **`LruCache`:** matches Android's algorithm (`put` / `get` / `create` race handling, `trimToSize` with the size
  sanity check, `entryRemoved` outside the lock as on Android), erased signatures match `android.jar`, so extension
  subclasses override correctly.
- **`Activity` / `Bundle` / `ApplicationInfo`:** binary-compatible signatures for the members used (`Bundle` final,
  `void` returns as on Android; `Activity` extends `ContextWrapper`); all under `android.`, so resolved from the host.
- **Install-failure display:** reason keyed by package name, first line only, cleared on success; no secrets in it
  (exception text may contain local paths, shown only on the user's own screen).
- **Cmd+H lock:** listener on the AWT event thread like the window listeners; removed on dispose; unsupported desktops
  get `null` and keep the minimise behaviour.
- **Self-test:** strict checks for the fixture; real extensions fail on host-library gaps and broken host members,
  not on known compat gaps (by design, but see D2); Injekt graph over a temporary data folder removed by a shutdown
  hook; failed checks write stack traces to the report (`18141a2e90`).
- **M2 review fixes:** dash titles kept out of `osascript` options with a marker and unmarked in the script (only the
  test coverage is weak, finding 10); one file dialog at a time (`81c414ff2a`) with a guard released in `finally`;
  `MangaCoverActionsUiTest` now waits on the saved file's full length instead of `exists()` (fixes the Linux race).
- **CI:** the self-test step uses `set -euo pipefail`, checks both the exit code and the report's last line, and fails
  when no report is written; actions are pinned by commit hash.
- **KMK markers, ownership, secrets:** the new code lives in desktop-only modules and `desktopApp` (no markers needed,
  as for the rest of those modules); the workflow edits are inside a `# KMK` block; no edits to Android-owned source
  outside borrowed files; no secrets, tokens or private paths in the diff.
