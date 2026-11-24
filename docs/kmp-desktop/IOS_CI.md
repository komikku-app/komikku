# iOS check (CI)

Status: **added, never run.** Nobody has seen the job pass yet, so it is non-blocking
(`continue-on-error: true`). PLAN.md section 8.4, step I5, should reference this page once it is
updated.

## What the job checks

Workflow `.github/workflows/build_ios_check.yml`, one job on `macos-15` (GitHub-hosted, Apple silicon):

| Task | What it proves |
|------|----------------|
| `:source-js:compileKotlinIosArm64` | `commonMain` + `iosMain` compile for iPhone devices |
| `:source-js:compileKotlinIosSimulatorArm64` | the same for the arm64 simulator |
| `:source-js:compileTestKotlinIosArm64` | `commonTest` + `iosTest` compile for devices |
| `:source-js:iosSimulatorArm64Test` | links the test binary (`linkDebugTestIosSimulatorArm64`, needs Xcode) and runs `commonTest` + `iosTest` on an iOS simulator |

The task names are the Kotlin Multiplatform plugin's conventions; they were checked with
`./gradlew -Pios-targets :source-js:tasks --all` (Kotlin 2.4.20).

It runs on `workflow_dispatch`, and on pull requests and pushes that touch `source-js/**`, `gradle/**` or the
workflow file. No signing, no secrets, no app (`:iosApp` is I6, an IPA is I7). Test reports are uploaded as
`ios-test-report-<sha>` when the job fails.

## Running it

```bash
gh workflow run build_ios_check.yml --ref claude/kmp-multiplatform-support
gh run list --workflow build_ios_check.yml --limit 3
gh run watch <run-id>
```

On a Mac, the same tasks run locally (the iOS targets are declared automatically on macOS hosts):

```bash
./gradlew :source-js:compileKotlinIosArm64 :source-js:iosSimulatorArm64Test --max-workers=2
```

### Compiling on Linux

`-Pios-targets` declares the iOS targets on any host (`source-js/build.gradle.kts`). Kotlin/Native's Linux
distribution ships the iOS platform libraries (`platform.Foundation`, `platform.posix`), so the main and test klibs
compile on Linux; only linking and the simulator run need macOS:

```bash
./gradlew -Pios-targets :source-js:compileKotlinIosArm64 :source-js:compileKotlinIosSimulatorArm64 \
  :source-js:compileTestKotlinIosArm64 :source-js:compileTestKotlinIosSimulatorArm64 --max-workers=2
```

This passed on Linux for the code added with this page (a deliberate typo in `iosMain` failed it, so the code is
really type-checked). The first run downloads the Kotlin/Native toolchain into `~/.konan`. Without the property,
Linux / Windows builds are unchanged.

## If it fails

1. **Configuration or toolchain**: the "Xcode and simulators" step prints the Xcode version and the available
   simulators. The Kotlin Gradle plugin picks its default test device from Xcode; if it cannot find one, set the
   test task's `device` (`tasks.withType<KotlinNativeSimulatorTest>`) to a listed simulator. A Kotlin / Xcode
   version mismatch shows as a warning from the Kotlin plugin before the link step.
2. **Compile errors** in `iosMain` / `iosTest`: reproduce on Linux with `-Pios-targets` (above).
3. **Link errors** (`linkDebugTestIosSimulatorArm64`): usually a dependency without an iOS simulator artifact
   (quickjs-kt, Ksoup, kotlinx). Only reproducible on macOS.
4. **Test failures or crashes**: download `ios-test-report-<sha>` (HTML report and JUnit XML). A crash with no
   report (the process dies) in a recursion test points at the engine thread's stack (below):
   `QuickJsEngineTest.deepRecursionIsAJsErrorNotACrash` runs with the full 4 MiB JS stack limit.
   Tests that rely on JVM-only behaviour belong in `jvmSharedTest` / `desktopTest`, not `commonTest`.

Once the job has passed a few times, remove `continue-on-error` so it gates pull requests touching `source-js/`.

## iOS engine thread (decision)

`newJsEngineThread` on iOS (`source-js/src/iosMain/.../engine/JsEngineThread.ios.kt`) used
`newSingleThreadContext`, which cannot set a thread stack size, so the JS stack limit was clamped to 256 KiB
(Darwin's secondary threads get 512 KiB). It now runs a small `CloseableCoroutineDispatcher` on an `NSThread`
whose `stackSize` is the JS limit plus 4 MiB headroom (as on the JVM), rounded up to 16 KiB; blocks wait in a
queue guarded by an `NSCondition`. The JS stack limit is therefore the same 4 MiB as on Android and desktop. The
public expect / actual signatures are unchanged.

- `NSThread` instead of a raw `pthread` (`pthread_attr_setstacksize`): no C function pointers or `StableRef`s, and
  the same stack-size control.
- Differences from the JVM thread: it does not end after 30 s idle (only when the engine closes it, like the old
  `newSingleThreadContext`). A block dispatched after `close()` cancels its job and runs on `Dispatchers.Default`,
  as the JVM executor dispatcher does with a rejected block.
- `iosTest/.../JsEngineThreadIosTest` checks the thread's name and stack size on the simulator.
- Risk: compiled (Linux klib), never executed. If the simulator tests crash in deep recursion, the stack size was
  not applied; the fallback is the old clamp (`minOf(jsStackBytes, 256 KiB)` with `newSingleThreadContext`).
