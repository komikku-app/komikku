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
 * compiled separately for each platform because it calls `androidOnly` declarations; `desktopCopy` =
 * still compiled by `app` for Android (unchanged) and additionally by `:app-shared`'s `desktopMain`
 * (Android-entangled files the desktop app needs; desktop counterparts of what they reference live in
 * app-shared/src/desktopMain/kotlin).
 * The build files apply this script through `applyAppSourceBorrowing()` (buildSrc
 * mihon/buildlogic/SourceBorrowing.kt), which reads the lists below.
 */
val shared = mutableListOf<String>()
val androidOnly = mutableListOf<String>()
val perPlatform = mutableListOf<String>()
val desktopCopy = mutableListOf<String>()
fun shared(vararg paths: String) = shared.addAll(paths)
fun androidOnly(vararg paths: String) = androidOnly.addAll(paths)
fun perPlatform(vararg paths: String) = perPlatform.addAll(paths)
fun desktopCopy(vararg paths: String) = desktopCopy.addAll(paths)

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

    // Source preferences and what they reference (5.2; extensions on desktop). SourceFilter moved to
    // app-shared/src/main/kotlin; SourcePreferences no longer calls the Android-only LocaleHelper.
    shared(
        "eu/kanade/domain/source/service/SourcePreferences.kt",
        "eu/kanade/domain/source/interactor/SetMigrateSorting.kt",
        "mihon/domain/migration/models/MigrationFlag.kt",
    )

    // Extension models and the platform-neutral extension interactors (5.2)
    shared(
        "eu/kanade/tachiyomi/extension/model/InstallStep.kt",
        "eu/kanade/tachiyomi/extension/model/LoadResult.kt",
        "eu/kanade/domain/extension/model/Extensions.kt",
        "eu/kanade/domain/extension/interactor/GetExtensionSources.kt",
        "eu/kanade/domain/extension/interactor/RefreshExtensionStores.kt",
        "exh/source/BlacklistedSources.kt",
    )

    // Manga updates (5.4; desktop manga details: add to library)
    shared(
        "eu/kanade/domain/manga/interactor/UpdateManga.kt",
    )

    // Manga screen enums (8.1; download menu actions)
    shared(
        "eu/kanade/presentation/manga/MangaScreenConstants.kt",
    )

    // Platform-neutral helpers used by app-shared's own reader / chapter code (6L.1, 7.1)
    shared(
        "eu/kanade/tachiyomi/util/chapter/ChapterRemoveDuplicates.kt",
        "exh/util/DataSaver.kt",
        "eu/kanade/tachiyomi/util/storage/OkioExtensions.kt",
    )

    // Manga / chapter interactors and what they reference (6L.1). Desktop counterparts in
    // app-shared/src/desktopMain: GLUtil, Context.hasMiuiPackageInstaller / isShizukuInstalled,
    // Source.isIncognitoModeEnabled, placeholders for the SY EHentai / MergedSource types.
    desktopCopy(
        "eu/kanade/domain/base/BasePreferences.kt",
        "eu/kanade/domain/base/ExtensionInstallerPreference.kt",
        "eu/kanade/domain/chapter/interactor/SetReadStatus.kt",
        "eu/kanade/domain/chapter/interactor/SyncChaptersWithSource.kt",
        "eu/kanade/domain/chapter/model/Chapter.kt",
        "eu/kanade/domain/download/interactor/DeleteDownload.kt",
        "eu/kanade/domain/manga/interactor/GetExcludedScanlators.kt",
        "eu/kanade/domain/manga/interactor/SetMangaViewerFlags.kt",
        "eu/kanade/domain/manga/model/Manga.kt",
        "eu/kanade/domain/source/interactor/GetIncognitoState.kt",
        "eu/kanade/tachiyomi/data/cache/CoverCache.kt",
        "eu/kanade/tachiyomi/data/database/models/Chapter.kt",
        "eu/kanade/tachiyomi/data/database/models/ChapterImpl.kt",
        "eu/kanade/tachiyomi/data/download/DownloadProvider.kt",
        "mihon/domain/source/interactor/UpdateMangaFromRemote.kt",
        "mihon/domain/source/models/RemoteMangaUpdate.kt",
    )

    // Reader models, page loaders, chapter cache and reader preferences (7.1). Desktop counterparts in
    // app-shared/src/desktopMain: ReadingMode (no Android viewers / icons), PagerConfig and
    // SubsamplingScaleImageView (constants only). ReaderOrientation uses the compat ActivityInfo.
    desktopCopy(
        "eu/kanade/tachiyomi/data/cache/ChapterCache.kt",
        "eu/kanade/tachiyomi/ui/reader/loader/ArchivePageLoader.kt",
        "eu/kanade/tachiyomi/ui/reader/loader/ChapterLoader.kt",
        "eu/kanade/tachiyomi/ui/reader/loader/DirectoryPageLoader.kt",
        "eu/kanade/tachiyomi/ui/reader/loader/DownloadPageLoader.kt",
        "eu/kanade/tachiyomi/ui/reader/loader/EpubPageLoader.kt",
        "eu/kanade/tachiyomi/ui/reader/loader/HttpPageLoader.kt",
        "eu/kanade/tachiyomi/ui/reader/loader/PageLoader.kt",
        "eu/kanade/tachiyomi/ui/reader/model/ChapterTransition.kt",
        "eu/kanade/tachiyomi/ui/reader/model/InsertPage.kt",
        "eu/kanade/tachiyomi/ui/reader/model/ReaderChapter.kt",
        "eu/kanade/tachiyomi/ui/reader/model/ReaderItem.kt",
        "eu/kanade/tachiyomi/ui/reader/model/ReaderPage.kt",
        "eu/kanade/tachiyomi/ui/reader/model/ViewerChapters.kt",
        "eu/kanade/tachiyomi/ui/reader/setting/ReaderBottomButton.kt",
        "eu/kanade/tachiyomi/ui/reader/setting/ReaderOrientation.kt",
        "eu/kanade/tachiyomi/ui/reader/setting/ReaderPreferences.kt",
        "eu/kanade/tachiyomi/ui/reader/viewer/MissingChapters.kt",
    )

    // Download queue, downloader, cache, store and pending deleter (8.1). Desktop counterparts in
    // app-shared/src/desktopMain: DownloadJob (coroutine runner instead of WorkManager), DownloadNotifier
    // (events for the desktop UI instead of notifications), NotificationHandler.openUrl,
    // LibraryUpdateNotifier.HELP_WARNING_URL, ExtensionManager (typealias of PlatformExtensionManager).
    desktopCopy(
        "eu/kanade/tachiyomi/data/download/DownloadCache.kt",
        "eu/kanade/tachiyomi/data/download/DownloadManager.kt",
        "eu/kanade/tachiyomi/data/download/DownloadPendingDeleter.kt",
        "eu/kanade/tachiyomi/data/download/DownloadStore.kt",
        "eu/kanade/tachiyomi/data/download/Downloader.kt",
        "eu/kanade/tachiyomi/data/download/model/Download.kt",
    )

    // Backup format, options, decoder, platform-neutral creators / restorers and the SY / EXH backup
    // migrations (8.2). They use `:data` (column adapters, DatabaseHandler), now an `:app-shared`
    // dependency on both targets.
    shared(
        "eu/kanade/tachiyomi/data/backup/models/Backup.kt",
        "eu/kanade/tachiyomi/data/backup/models/BackupCategory.kt",
        "eu/kanade/tachiyomi/data/backup/models/BackupChapter.kt",
        "eu/kanade/tachiyomi/data/backup/models/BackupExtensionStore.kt",
        "eu/kanade/tachiyomi/data/backup/models/BackupFeed.kt",
        "eu/kanade/tachiyomi/data/backup/models/BackupFlatMetadata.kt",
        "eu/kanade/tachiyomi/data/backup/models/BackupHistory.kt",
        "eu/kanade/tachiyomi/data/backup/models/BackupManga.kt",
        "eu/kanade/tachiyomi/data/backup/models/BackupMergedMangaReference.kt",
        "eu/kanade/tachiyomi/data/backup/models/BackupPreference.kt",
        "eu/kanade/tachiyomi/data/backup/models/BackupSavedSearch.kt",
        "eu/kanade/tachiyomi/data/backup/models/BackupSource.kt",
        "eu/kanade/tachiyomi/data/backup/models/BackupTracking.kt",
        "eu/kanade/tachiyomi/data/backup/models/metadata/BackupSearchMetadata.kt",
        "eu/kanade/tachiyomi/data/backup/models/metadata/BackupSearchTag.kt",
        "eu/kanade/tachiyomi/data/backup/models/metadata/BackupSearchTitle.kt",
        "exh/EXHMigrations.kt",
        "eu/kanade/tachiyomi/data/backup/BackupDecoder.kt",
        "eu/kanade/tachiyomi/data/backup/create/BackupOptions.kt",
        "eu/kanade/tachiyomi/data/backup/restore/RestoreOptions.kt",
        "eu/kanade/tachiyomi/data/backup/create/creators/CategoriesBackupCreator.kt",
        "eu/kanade/tachiyomi/data/backup/create/creators/ExtensionStoresBackupCreator.kt",
        "eu/kanade/tachiyomi/data/backup/create/creators/FeedBackupCreator.kt",
        "eu/kanade/tachiyomi/data/backup/create/creators/PreferenceBackupCreator.kt",
        "eu/kanade/tachiyomi/data/backup/create/creators/SavedSearchBackupCreator.kt",
        "eu/kanade/tachiyomi/data/backup/create/creators/SourcesBackupCreator.kt",
        "eu/kanade/tachiyomi/data/backup/restore/restorers/CategoriesRestorer.kt",
        "eu/kanade/tachiyomi/data/backup/restore/restorers/ExtensionStoreRestorer.kt",
        "eu/kanade/tachiyomi/data/backup/restore/restorers/FeedRestorer.kt",
        "eu/kanade/tachiyomi/data/backup/restore/restorers/MangaRestorer.kt",
        "eu/kanade/tachiyomi/data/backup/restore/restorers/SavedSearchRestorer.kt",
    )

    // Backup create / restore entry points and what references Android-only code (8.2): BuildConfig,
    // BackupNotifier / Notifications, TrackerManager, LibraryUpdateJob / BackupCreateJob / AppUpdateJob,
    // AndroidPreferenceStore, ReadingMode. Desktop counterparts in app-shared/src/desktopMain.
    desktopCopy(
        "eu/kanade/tachiyomi/data/backup/BackupFileValidator.kt",
        "eu/kanade/tachiyomi/data/backup/create/BackupCreator.kt",
        "eu/kanade/tachiyomi/data/backup/create/creators/MangaBackupCreator.kt",
        "eu/kanade/tachiyomi/data/backup/restore/BackupRestorer.kt",
        "eu/kanade/tachiyomi/data/backup/restore/restorers/PreferenceRestorer.kt",
    )

    // Tracker helpers without Android dependencies (8.3)
    shared(
        "exh/md/utils/FollowStatus.kt",
        "eu/kanade/tachiyomi/util/PkceUtil.kt",
        "eu/kanade/tachiyomi/util/lang/DateExtensions.kt",
    )

    // Trackers, their models / APIs, tracker preferences and the track interactors (8.3). Desktop
    // counterparts in app-shared/src/desktopMain: TrackerManager (same trackers + test hook; MDList is the borrowed
    // tracker since P4, below with the MangaDex delegate it needs),
    // DelayedTrackingUpdateJob (coroutine retry instead of WorkManager), R.drawable (tracker logo ids).
    desktopCopy(
        "eu/kanade/tachiyomi/data/track/BaseTracker.kt",
        "eu/kanade/tachiyomi/data/track/DeletableTracker.kt",
        "eu/kanade/tachiyomi/data/track/EnhancedTracker.kt",
        "eu/kanade/tachiyomi/data/track/TrackStatus.kt",
        "eu/kanade/tachiyomi/data/track/Tracker.kt",
        "eu/kanade/tachiyomi/data/track/anilist/Anilist.kt",
        "eu/kanade/tachiyomi/data/track/anilist/AnilistApi.kt",
        "eu/kanade/tachiyomi/data/track/anilist/AnilistInterceptor.kt",
        "eu/kanade/tachiyomi/data/track/anilist/AnilistUtils.kt",
        "eu/kanade/tachiyomi/data/track/anilist/dto/ALAddManga.kt",
        "eu/kanade/tachiyomi/data/track/anilist/dto/ALError.kt",
        "eu/kanade/tachiyomi/data/track/anilist/dto/ALFuzzyDate.kt",
        "eu/kanade/tachiyomi/data/track/anilist/dto/ALManga.kt",
        "eu/kanade/tachiyomi/data/track/anilist/dto/ALMangaMetadata.kt",
        "eu/kanade/tachiyomi/data/track/anilist/dto/ALOAuth.kt",
        "eu/kanade/tachiyomi/data/track/anilist/dto/ALSearch.kt",
        "eu/kanade/tachiyomi/data/track/anilist/dto/ALSearchItem.kt",
        "eu/kanade/tachiyomi/data/track/anilist/dto/ALUser.kt",
        "eu/kanade/tachiyomi/data/track/anilist/dto/ALUserList.kt",
        "eu/kanade/tachiyomi/data/track/bangumi/Bangumi.kt",
        "eu/kanade/tachiyomi/data/track/bangumi/BangumiApi.kt",
        "eu/kanade/tachiyomi/data/track/bangumi/BangumiInterceptor.kt",
        "eu/kanade/tachiyomi/data/track/bangumi/BangumiUtils.kt",
        "eu/kanade/tachiyomi/data/track/bangumi/dto/BGMCollectionResponse.kt",
        "eu/kanade/tachiyomi/data/track/bangumi/dto/BGMOAuth.kt",
        "eu/kanade/tachiyomi/data/track/bangumi/dto/BGMSearch.kt",
        "eu/kanade/tachiyomi/data/track/bangumi/dto/BGMSubject.kt",
        "eu/kanade/tachiyomi/data/track/bangumi/dto/BGMUser.kt",
        "eu/kanade/tachiyomi/data/track/kavita/Kavita.kt",
        "eu/kanade/tachiyomi/data/track/kavita/KavitaApi.kt",
        "eu/kanade/tachiyomi/data/track/kavita/KavitaInterceptor.kt",
        "eu/kanade/tachiyomi/data/track/kavita/KavitaModels.kt",
        "eu/kanade/tachiyomi/data/track/kitsu/Kitsu.kt",
        "eu/kanade/tachiyomi/data/track/kitsu/KitsuApi.kt",
        "eu/kanade/tachiyomi/data/track/kitsu/KitsuDateHelper.kt",
        "eu/kanade/tachiyomi/data/track/kitsu/KitsuInterceptor.kt",
        "eu/kanade/tachiyomi/data/track/kitsu/KitsuUtils.kt",
        "eu/kanade/tachiyomi/data/track/kitsu/dto/KitsuAddManga.kt",
        "eu/kanade/tachiyomi/data/track/kitsu/dto/KitsuListSearch.kt",
        "eu/kanade/tachiyomi/data/track/kitsu/dto/KitsuMangaMetadata.kt",
        "eu/kanade/tachiyomi/data/track/kitsu/dto/KitsuOAuth.kt",
        "eu/kanade/tachiyomi/data/track/kitsu/dto/KitsuSearch.kt",
        "eu/kanade/tachiyomi/data/track/kitsu/dto/KitsuSearchItemCover.kt",
        "eu/kanade/tachiyomi/data/track/kitsu/dto/KitsuUser.kt",
        "eu/kanade/tachiyomi/data/track/komga/Komga.kt",
        "eu/kanade/tachiyomi/data/track/komga/KomgaApi.kt",
        "eu/kanade/tachiyomi/data/track/komga/KomgaModels.kt",
        "eu/kanade/tachiyomi/data/track/mangaupdates/MangaUpdates.kt",
        "eu/kanade/tachiyomi/data/track/mangaupdates/MangaUpdatesApi.kt",
        "eu/kanade/tachiyomi/data/track/mangaupdates/MangaUpdatesInterceptor.kt",
        "eu/kanade/tachiyomi/data/track/mangaupdates/dto/MUContext.kt",
        "eu/kanade/tachiyomi/data/track/mangaupdates/dto/MUImage.kt",
        "eu/kanade/tachiyomi/data/track/mangaupdates/dto/MUListItem.kt",
        "eu/kanade/tachiyomi/data/track/mangaupdates/dto/MULoginResponse.kt",
        "eu/kanade/tachiyomi/data/track/mangaupdates/dto/MURating.kt",
        "eu/kanade/tachiyomi/data/track/mangaupdates/dto/MURecord.kt",
        "eu/kanade/tachiyomi/data/track/mangaupdates/dto/MUSearch.kt",
        "eu/kanade/tachiyomi/data/track/mangaupdates/dto/MUSeries.kt",
        "eu/kanade/tachiyomi/data/track/mangaupdates/dto/MUStatus.kt",
        "eu/kanade/tachiyomi/data/track/mangaupdates/dto/MUUrl.kt",
        "eu/kanade/tachiyomi/data/track/model/TrackMangaMetadata.kt",
        "eu/kanade/tachiyomi/data/track/model/TrackSearch.kt",
        "eu/kanade/tachiyomi/data/track/myanimelist/MyAnimeList.kt",
        "eu/kanade/tachiyomi/data/track/myanimelist/MyAnimeListApi.kt",
        "eu/kanade/tachiyomi/data/track/myanimelist/MyAnimeListInterceptor.kt",
        "eu/kanade/tachiyomi/data/track/myanimelist/MyAnimeListUtils.kt",
        "eu/kanade/tachiyomi/data/track/myanimelist/dto/MALList.kt",
        "eu/kanade/tachiyomi/data/track/myanimelist/dto/MALManga.kt",
        "eu/kanade/tachiyomi/data/track/myanimelist/dto/MALOAuth.kt",
        "eu/kanade/tachiyomi/data/track/myanimelist/dto/MALSearch.kt",
        "eu/kanade/tachiyomi/data/track/myanimelist/dto/MALUser.kt",
        "eu/kanade/tachiyomi/data/track/shikimori/Shikimori.kt",
        "eu/kanade/tachiyomi/data/track/shikimori/ShikimoriApi.kt",
        "eu/kanade/tachiyomi/data/track/shikimori/ShikimoriInterceptor.kt",
        "eu/kanade/tachiyomi/data/track/shikimori/ShikimoriUtils.kt",
        "eu/kanade/tachiyomi/data/track/shikimori/dto/SMAddMangaResponse.kt",
        "eu/kanade/tachiyomi/data/track/shikimori/dto/SMManga.kt",
        "eu/kanade/tachiyomi/data/track/shikimori/dto/SMMetadata.kt",
        "eu/kanade/tachiyomi/data/track/shikimori/dto/SMOAuth.kt",
        "eu/kanade/tachiyomi/data/track/shikimori/dto/SMUser.kt",
        "eu/kanade/tachiyomi/data/track/shikimori/dto/SMUserListEntry.kt",
        "eu/kanade/tachiyomi/data/track/suwayomi/Suwayomi.kt",
        "eu/kanade/tachiyomi/data/track/suwayomi/SuwayomiApi.kt",
        "eu/kanade/tachiyomi/data/track/suwayomi/SuwayomiModels.kt",
        "eu/kanade/tachiyomi/data/database/models/Track.kt",
        "eu/kanade/tachiyomi/data/database/models/TrackImpl.kt",
        "eu/kanade/domain/track/interactor/AddTracks.kt",
        "eu/kanade/domain/track/interactor/RefreshTracks.kt",
        "eu/kanade/domain/track/interactor/SyncChapterProgressWithTrack.kt",
        "eu/kanade/domain/track/interactor/TrackChapter.kt",
        "eu/kanade/domain/track/model/AutoTrackState.kt",
        "eu/kanade/domain/track/model/Track.kt",
        "eu/kanade/domain/track/service/TrackPreferences.kt",
        "eu/kanade/domain/track/store/DelayedTrackingStore.kt",
    )

    // Data-driven settings model and its widgets (8.4; desktop settings screens are built from the same
    // `Preference` items). Desktop counterparts in app-shared/src/desktopMain: ConnectionsService (type
    // only, no connections on desktop), ConnectionLogoIcon and TrackLogoIcon (name initials, no logos).
    desktopCopy(
        "eu/kanade/presentation/more/settings/Preference.kt",
        "eu/kanade/presentation/more/settings/PreferenceItem.kt",
        "eu/kanade/presentation/more/settings/widget/AppThemeModePreferenceWidget.kt",
        "eu/kanade/presentation/more/settings/widget/BasePreferenceWidget.kt",
        "eu/kanade/presentation/more/settings/widget/ConnectionPreferenceWidget.kt",
        "eu/kanade/presentation/more/settings/widget/EditTextPreferenceWidget.kt",
        "eu/kanade/presentation/more/settings/widget/InfoWidget.kt",
        "eu/kanade/presentation/more/settings/widget/ListPreferenceWidget.kt",
        "eu/kanade/presentation/more/settings/widget/MultiSelectListPreferenceWidget.kt",
        "eu/kanade/presentation/more/settings/widget/PreferenceGroupHeader.kt",
        "eu/kanade/presentation/more/settings/widget/SwitchPreferenceWidget.kt",
        "eu/kanade/presentation/more/settings/widget/TextPreferenceWidget.kt",
        "eu/kanade/presentation/more/settings/widget/TrackingPreferenceWidget.kt",
        "eu/kanade/presentation/more/settings/widget/TriStateListDialog.kt",
    )

    // Library filter / sort / text search (8.5): LibraryFilterSort.kt holds the logic moved out of
    // LibraryScreenModel, the SY query parser it uses, the library settings model (filter toggles, sort,
    // display mode) and their helpers. No desktop counterparts needed.
    desktopCopy(
        "eu/kanade/core/preference/PreferenceMutableState.kt",
        "eu/kanade/core/util/CollectionUtils.kt",
        "eu/kanade/tachiyomi/ui/library/LibraryFilterSort.kt",
        "eu/kanade/tachiyomi/ui/library/LibraryItem.kt",
        "eu/kanade/tachiyomi/ui/library/LibrarySettingsScreenModel.kt",
        "exh/search/MultiWildcard.kt",
        "exh/search/Namespace.kt",
        "exh/search/QueryComponent.kt",
        "exh/search/SearchEngine.kt",
        "exh/search/SingleWildcard.kt",
        "exh/search/StringTextComponent.kt",
        "exh/search/Text.kt",
        "exh/search/TextComponent.kt",
        "exh/util/CoroutineUtil.kt",
        "exh/util/LewdMangaChecker.kt",
    )

    // Source toggles / pins (P1): the toggle interactors behind Android's sources tab and source filter
    // screen, and the global search result rows.
    shared(
        "eu/kanade/domain/source/interactor/ToggleLanguage.kt",
        "eu/kanade/domain/source/interactor/ToggleSource.kt",
        "eu/kanade/domain/source/interactor/ToggleSourcePin.kt",
        "eu/kanade/presentation/browse/components/GlobalSearchResultItems.kt",
    )

    // Enabled-sources query (uses `:source-local`, a desktop-only dependency of `:app-shared`), source
    // filter screen model and global search screen models (P1). Desktop counterparts in
    // app-shared/src/desktopMain: LocaleHelper (language names and order without Android's locale list),
    // ExtensionManager (typealias of PlatformExtensionManager, see 8.1).
    desktopCopy(
        "eu/kanade/domain/source/interactor/GetEnabledSources.kt",
        "eu/kanade/domain/source/interactor/GetLanguagesWithSources.kt",
        "eu/kanade/tachiyomi/ui/browse/source/SourcesFilterScreenModel.kt",
        "eu/kanade/tachiyomi/ui/browse/source/globalsearch/GlobalSearchScreenModel.kt",
        "eu/kanade/tachiyomi/ui/browse/source/globalsearch/SearchScreenModel.kt",
    )

    // Library grouping, selection and batch actions (P2): LibraryActions.kt holds the selection / batch-action logic
    // moved out of LibraryScreenModel (grouping moved into LibraryFilterSort.kt above), the cover helper it uses, and
    // the change-category and remove dialogs with their helpers. Desktop counterpart in app-shared/src/desktopMain:
    // CategoryExtensions (`visualName`; the Android file's two `stringResource` imports are ambiguous on desktop).
    desktopCopy(
        "eu/kanade/core/preference/CheckboxState.kt",
        "eu/kanade/presentation/category/components/CategoryDialogs.kt",
        "eu/kanade/presentation/components/DownloadDropdownMenu.kt",
        "eu/kanade/presentation/components/DropdownMenu.kt",
        "eu/kanade/presentation/library/DeleteLibraryMangaDialog.kt",
        "eu/kanade/tachiyomi/ui/library/LibraryActions.kt",
        "eu/kanade/tachiyomi/util/MangaExtensions.kt",
    )

    // SY delegated (enhanced) sources applied over installed extension sources (P4), with the URL import they search
    // through (SearchOverride.kt → GalleryAdder); MangaDex in the next block. Desktop counterpart in
    // app-shared/src/desktopMain: the EHentai placeholder (AbsentSources.kt) with `GalleryNotFoundException`, which
    // GalleryAdder tests for.
    desktopCopy(
        "eu/kanade/tachiyomi/source/online/all/Lanraragi.kt",
        "eu/kanade/tachiyomi/source/online/all/NHentai.kt",
        "eu/kanade/tachiyomi/source/online/all/Pururin.kt",
        "eu/kanade/tachiyomi/source/online/english/EightMuses.kt",
        "exh/GalleryAdder.kt",
        "exh/util/SearchOverride.kt",
    )

    // The MangaDex delegate (P4) with its API layer and the MDList tracker it logs in / syncs through (replaces the
    // desktop MDList stub). Not borrowed: MangaDexLoginActivity (the desktop logs in through Settings → Tracking, an
    // OAuth paste handed to the MangaDex source's login, `DesktopTrackerLogin`), the follows / similar screens and their
    // paging sources.
    desktopCopy(
        "eu/kanade/tachiyomi/data/track/mdlist/MdList.kt",
        "eu/kanade/tachiyomi/source/online/all/MangaDex.kt",
        "exh/md/dto/AtHomeDto.kt",
        "exh/md/dto/ChapterDto.kt",
        "exh/md/dto/ListCallDto.kt",
        "exh/md/dto/MangaDto.kt",
        "exh/md/dto/MangaPlusDto.kt",
        "exh/md/dto/RatingDto.kt",
        "exh/md/dto/ResultDto.kt",
        "exh/md/dto/SimilarDto.kt",
        "exh/md/dto/StatisticsDto.kt",
        "exh/md/handlers/ApiMangaParser.kt",
        "exh/md/handlers/AzukiHandler.kt",
        "exh/md/handlers/BilibiliHandler.kt",
        "exh/md/handlers/ComikeyHandler.kt",
        "exh/md/handlers/FilterHandler.kt",
        "exh/md/handlers/FollowsHandler.kt",
        "exh/md/handlers/MangaHandler.kt",
        "exh/md/handlers/MangaHotHandler.kt",
        "exh/md/handlers/MangaPlusHandler.kt",
        "exh/md/handlers/NamicomiHandler.kt",
        "exh/md/handlers/PageHandler.kt",
        "exh/md/handlers/SimilarHandler.kt",
        "exh/md/network/MangaDexAuthInterceptor.kt",
        "exh/md/network/MangaDexLoginHelper.kt",
        "exh/md/service/MangaDexAuthService.kt",
        "exh/md/service/MangaDexService.kt",
        "exh/md/service/SimilarService.kt",
        "exh/md/utils/MdApi.kt",
        "exh/md/utils/MdConstants.kt",
        "exh/md/utils/MdExtensions.kt",
        "exh/md/utils/MdLang.kt",
        "exh/md/utils/MdUtil.kt",
        "exh/util/Boolean.kt",
        "exh/util/Math.kt",
    )
}

extra["borrowedAppSources.shared"] = shared.toList()
extra["borrowedAppSources.androidOnly"] = androidOnly.toList()
extra["borrowedAppSources.perPlatform"] = perPlatform.toList()
extra["borrowedAppSources.desktopCopy"] = desktopCopy.toList()
