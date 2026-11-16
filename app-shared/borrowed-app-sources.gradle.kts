/*
 * KMK: the single allowlist of `app` source files that `:app-shared` compiles for Android and desktop
 * ("source borrowing", docs/kmp-desktop/PLAN.md D5). Applied by both `app/build.gradle.kts` and
 * `app-shared/build.gradle.kts`: `app` excludes exactly these files from its own compilation and gets
 * them through its `:app-shared` dependency; `:app-shared` compiles them from their upstream paths.
 *
 * Paths are relative to `app/src/main/java`. A file may only be borrowed when it compiles on desktop
 * (no `Context`-bound Android APIs, `R`, activities; Android-only symbols must be covered by
 * `:desktop:android-compat` / `:desktop:compose-compat`) and does not use `internal` declarations of
 * `app` that stay behind. `./gradlew :app:verifyBorrowedSources` (also part of `:app:check`) fails
 * when a listed file is missing or would be compiled by both modules or by neither.
 *
 * Kinds: `shared` = compiled once for Android and desktop (`jvmSharedMain`); `androidOnly` = Android
 * only (`androidMain`; desktop counterparts live in app-shared/src/desktopMain); `perPlatform` =
 * compiled separately for each platform because it calls `androidOnly` declarations.
 * The build files apply this script through `applyAppSourceBorrowing()` (buildSrc
 * mihon/buildlogic/SourceBorrowing.kt), which reads the lists below.
 */
val shared = mutableListOf<String>()
val androidOnly = mutableListOf<String>()
val perPlatform = mutableListOf<String>()
fun shared(vararg paths: String) = shared.addAll(paths)
fun androidOnly(vararg paths: String) = androidOnly.addAll(paths)
fun perPlatform(vararg paths: String) = perPlatform.addAll(paths)

run {
    // UI preferences and their models (4.2; theme)
    shared(
        "eu/kanade/domain/ui/UiPreferences.kt",
        "eu/kanade/domain/ui/model/AppTheme.kt",
        "eu/kanade/domain/ui/model/TabletUiMode.kt",
        "eu/kanade/domain/ui/model/ThemeMode.kt",
    )

    // Color schemes (4.2; theme). AndroidViewColorScheme.kt (Android views) stays in app.
    shared(
        "eu/kanade/presentation/theme/colorscheme/BaseColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/CatppuccinColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/CloudflareColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/CottoncandyColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/CustomColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/DoomColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/GreenAppleColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/LavenderColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/MatrixColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/MidnightDuskColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/MochaColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/MonochromeColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/NordColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/SapphireColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/StrawberryColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/TachiyomiColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/TakoColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/TealTurqoiseColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/TidalWaveColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/YinYangColorScheme.kt",
        "eu/kanade/presentation/theme/colorscheme/YotsubaColorScheme.kt",
    )

    // Monet (wallpaper / system dynamic colors) is Android-only; desktop counterpart in
    // app-shared/src/desktopMain/kotlin/eu/kanade/presentation/theme/colorscheme/MonetColorScheme.kt.
    androidOnly(
        "eu/kanade/presentation/theme/colorscheme/MonetColorScheme.kt",
    )

    // TachiyomiTheme calls MonetColorScheme, so it is compiled per platform.
    perPlatform(
        "eu/kanade/presentation/theme/TachiyomiTheme.kt",
    )

    // Voyager base types and screen transitions (4.2; navigator host)
    shared(
        "eu/kanade/presentation/util/Navigator.kt",
    )
}

extra["borrowedAppSources.shared"] = shared.toList()
extra["borrowedAppSources.androidOnly"] = androidOnly.toList()
extra["borrowedAppSources.perPlatform"] = perPlatform.toList()
