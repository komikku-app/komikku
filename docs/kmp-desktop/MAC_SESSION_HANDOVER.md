# Handover to a session running on the maintainer's Mac (2026-11-27)

Written by the cloud session that built the desktop port so far. It had no Mac: every macOS fix was made blind and
checked only by CI. You run on the maintainer's Mac, so you can build, start and use the app yourself. Read
[`AGENTS.md`](../../AGENTS.md) first, then this page, then [`HANDOVER.md`](HANDOVER.md) ("Working agreement" and
"Next session (start here)"), [`BUILDING.md`](BUILDING.md) and [`TESTING_MAC.md`](TESTING_MAC.md).

## State

- Branch `claude/kmp-multiplatform-support`, HEAD `365f98a` when this was written (plus this page), in sync with origin.
  Push only to this branch. No pull request, no tags, no releases, no force-push, no rebase of pushed commits.
- Another session pushes iOS commits to the same branch (`source-js/**/ios*`, `.github/workflows/build_ios_check.yml`,
  `docs/kmp-desktop/IOS_CI.md`): leave those files alone; when a push is rejected,
  `git fetch origin claude/kmp-multiplatform-support && git merge --no-edit FETCH_HEAD`, then push again.
- The cloud session has one last run scheduled for 2026-11-28 02:00 (UTC+7): a code review of `cbdfdff..365f98a`
  (the M2 fixes) and its fix round. It may push small fixes to this branch around then (UI one-line text, the macOS
  notifier, the folder dialog, the self-test). Merge them; it has been told nothing about the open bug below.
- Last full `:desktopApp:test` (Linux): 86 classes, 396 tests, 0 failures on `5e9af6b`. Last green CI desktop build:
  <https://github.com/komikku-app/komikku/actions/runs/45938000837> (commit `9b58d72`; `.dmg` for Apple silicon and
  Intel; this is the build the maintainer tested).
- CI: `build_desktop.yml` runs on this branch only when `.github/desktop-build-trigger` changes (bump the counter in
  it); branch runs never touch a release. All jobs run on GitHub-hosted runners. A change that moves the macOS jobs to
  the maintainer's self-hosted runner is parked on the branch `claude/kmp-self-hosted-mac-runner` (commit `98e84f7`)
  and must stay parked until the maintainer says the runner is free.

## The open bug: APK extensions cannot be installed in the packaged app

JavaScript (Mangayomi) sources work. APK extensions from the Keiyoushi repository download but fail to load. The
maintainer has now lost two test rounds on this, so reproduce it on the Mac before changing anything, and confirm the
fix in the packaged app before reporting it fixed.

**Round 1** (build of run 45832027995): `dex2jar failed …: cant find zipfs support`. The jlink'd runtime lacked
`jdk.zipfs`. Fixed in M2 (`desktopApp/runtime-modules.txt`, `Komikku --self-test`, a CI step that runs the self-test
with the packaged launcher on all four runners).

**Round 2** (build of run 45938000837, commit `9b58d72`), from the maintainer's
`~/Library/Application Support/Komikku/logs/komikku.log`:

```
E/DesktopExtensionManager: Extension load error: Hiperdex (eu.kanade.tachiyomi.extension.en.hiperdex)
app.komikku.desktop.extension.ExtensionLoadException: Extension load error: Hiperdex (…): java.lang.VerifyError: Expecting a stackmap frame at branch target 30
Exception Details:
  Location:
    v0.getSearchManga(ILjava/lang/String;Leu/kanade/tachiyomi/source/model/FilterList;Lkotlin/coroutines/Continuation;)Ljava/lang/Object; @15: ifnull
  Reason:
    Expected stackmap frame at this location.
    at app.komikku.desktop.extension.ExtensionRuntime.load(ExtensionRuntime.kt:71)
    at app.komikku.desktop.extensions.DesktopExtensionManager.loadExtension(DesktopExtensionManager.kt:187)
    at app.komikku.desktop.extensions.DesktopExtensionManager.installApkFile(DesktopExtensionManager.kt:415)
Caused by: java.lang.VerifyError: …
    at java.base/java.lang.Class.getDeclaredConstructors0(Native Method)
    at app.komikku.desktop.extension.ExtensionRuntime$Companion.instantiateSources(ExtensionRuntime.kt:83)
```

What this says: the conversion now succeeds, and the JVM's bytecode verifier rejects a class from the converted jar
because it has no `StackMapTable` where one is required. Class files of version 51 and newer must carry stack map
frames; version 50 classes fall back to the old type-inferring verifier.

**Not known yet** (nothing below is verified; the cloud session was stopped before it reproduced anything):

- Why development runs and the tests never showed it. `desktopApp/build.gradle.kts` sets no `-Xverify:none` /
  `-noverify` for either the run task or the packaged launcher, so that is probably not the difference. Candidates:
  the test and self-test fixture extension has no method that needs a frame (the failing one is a suspend method with
  a branch); only some extensions are affected (`v0` is a minified class name, so the extension was built with R8);
  the class version dex2jar writes differs by input; the JVM version differs between the bundled runtime and the build JDK.
- Whether the first extension (Kagane) fails the same way now.
- Whether it is specific to the packaged app at all. Check `./gradlew :desktopApp:run` on the Mac with Hiperdex first.

**Where to look:** `:desktop:extension-runtime` (`DexJarConverter`, `ExtensionRuntime`),
`desktopApp/.../extensions/DesktopExtensionManager.kt`, `desktopApp/.../selftest/DesktopSelfTest.kt` and its fixture
writer, and PROGRESS.md for how the converter was built and which extensions it was tried with.

**What a good fix looks like** (the coordinator's view, to be checked against what you find):

- Converted classes must be verifiable. Either rewrite each converted class through ASM with `COMPUTE_FRAMES` (with a
  `getCommonSuperClass` that resolves against the extension's own classes and the app's class path without
  initialising classes, falling back to `java/lang/Object`), or write them as version 50 so the old verifier applies
  (check the bundled JDK still honours that fallback). Do not switch verification off for the whole app unless nothing
  else works, and record why.
- Invalidate the converted-jar cache (bump its version key) so jars already converted in the maintainer's data folder
  are regenerated.
- The self-test and CI must catch this class of failure: verify every class of the converted jar (not only the entry
  class), and convert and load real extensions (Hiperdex and Kagane at least) inside the packaged app. Releases of the
  Keiyoushi repository can be downloaded from CI.
- A failed install should show the real error in the app (first line of the cause, and the log path), not only fail.

## Still to confirm on the Mac (fixed blind in M2, never seen running)

1. **Folder picker:** Settings → downloads folder should open the native macOS folder panel, and a folder made with
   "New Folder" should show at once.
2. **Notification:** a library update with new chapters should post a system notification through `osascript`,
   attributed to Script Editor (the app is unsigned), possibly after a permission prompt.
3. **Narrow window:** the maintainer confirmed this one is fixed.

Also worth a look, listed as suspect by the static review and never observed: Cmd+Q skipping the app's bounded
shutdown; the menu bar icon being a colour image (no template image; needs a designed monochrome icon); app lock
reacting to minimise but not to Cmd+H; whether the unsigned app starts cleanly after the quarantine flag is removed.

## Working on the Mac

- JDK 21 (`.github/.java-version`). `./gradlew :desktopApp:run` starts the app from sources;
  `./gradlew :desktopApp:createDistributable` builds `Komikku.app` under `desktopApp/build/compose/binaries/main/app/`
  (this is the packaged runtime: test fixes for packaged-only bugs with it, e.g.
  `…/Komikku.app/Contents/MacOS/Komikku --self-test`); `./gradlew :desktopApp:packageDmg` builds the installer.
- Use a separate data folder so the maintainer's own library is not touched:
  `JAVA_TOOL_OPTIONS=-Dkomikku.dataDir=/tmp/komikku-test <launcher>` (see TESTING_MAC.md "Data and logs").
- Gates before a change is done: `./gradlew spotlessApply`, `spotlessCheck`, `:app:verifyBorrowedSources`,
  `:app:compileDebugKotlin` when `app/` or a shared module changed, `:desktopApp:compileKotlin`, the targeted tests,
  and a full `:desktopApp:test` before hand-back. The earlier rule "no `:desktopApp:run`" was only because the cloud
  session had no display; it does not apply on the Mac.
- Rules that still apply: KMK markers around changes in upstream files, new strings only in `i18n-kmk` base, `xLog`
  logging, small commits pushed as you go, no AI attribution or model names in commits or files, decisions recorded in
  PROGRESS.md, HANDOVER.md kept current.
