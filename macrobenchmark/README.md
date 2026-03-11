# Baseline profiles

The baseline profile for this app is located at [`app/src/main/baseline-prof.txt`](../app/src/main/baseline-prof.txt).
It contains rules that enable AOT compilation of the critical user path taken during app launch.
For more information on baseline profiles, read [this document](https://developer.android.com/studio/profile/baselineprofiles).

> Note: The baseline profile needs to be re-generated for release builds that touch code which changes app startup.

To generate the baseline profile, select the `benchmark` build variant and run the
`BaselineProfileGenerator` benchmark test on an AOSP Android Emulator.
Then copy the resulting baseline profile from the emulator to [`app/src/main/baseline-prof.txt`](../app/src/main/baseline-prof.txt).

---

To run the BaselineProfileGenerator benchmark test to generate a baseline profile for Mihon, follow these steps:

## 1. Prepare the AOSP Emulator

* **Use an AOSP Image**: Ensure your emulator is running an AOSP (Android Open Source Project) system image (e.g., "Android 13.0 (AOSP)"), which does not include Google Play services. This is recommended by the benchmark library for more accurate and stable results.
* **Root Access**: AOSP emulators are typically rooted, which allows the benchmark to reset the app state and compile it between runs.

## 2. Set the Correct Build Variant

   In Android Studio:

1. Open the **Build Variants** tool window (usually on the bottom left).
2. For the `:app` module, select the `benchmark` variant.
3. The `:macrobenchmark` module should automatically switch to its benchmark variant.

## 3. Run the Test

You can run the generator either from the IDE or the command line:
> [!TIP]
>
> You might need to manually build & run `benchmark` variant on your device, then configure `Storage permission` and restore backup as well as install extensions before starting test.

**From Android Studio (Recommended)**

1. Navigate to [BaselineProfileGenerator](src/main/java/tachiyomi/macrobenchmark/BaselineProfileGenerator.kt).
2. Click the **Run** icon (green play button) in the gutter next to the class name `BaselineProfileGenerator`.
3. Select **Run 'BaselineProfileGenerator'**.

**From Terminal**
Run the following command from the project root:

` ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest `

## 4. Collect the Profile

   Once the test finishes:

1. The generated profile will be saved on the emulator.
2. Look for a link in the test output/logs in Android Studio. It usually provides a path to the generated file or even a command to pull it.
3. Alternatively, check the build folder: `macrobenchmark/build/outputs/connected_android_test_additional_output/benchmark/connected/<device>/BaselineProfileGenerator_generate-baseline-prof.txt`.
4. Copy the contents to `app/src/main/baseline-prof.txt`.
