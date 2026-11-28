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

    // Library filter / sort / text search (8.5): the SY query parser the desktop copy LibraryFilterSort.kt
    // (app-shared/src/desktopMain) uses, the library settings model (filter toggles, sort, display mode) and their
    // helpers. No desktop counterparts needed.
    desktopCopy(
        "eu/kanade/core/preference/PreferenceMutableState.kt",
        "eu/kanade/core/util/CollectionUtils.kt",
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

    // Saved searches in the catalogue (P13): SY's loader (saved row + filter JSON → query and filter list) and the
    // create / delete dialogs of Android's browse screen.
    shared(
        "eu/kanade/domain/source/interactor/GetExhSavedSearch.kt",
        "eu/kanade/presentation/browse/components/BrowseSourceDialogs.kt",
        // SY's auto-complete filter row, used by Android's filter sheet and by the desktop copy of its filter rows
        // (SourceFilterItems.kt in app-shared/src/desktopMain) in the desktop catalogue's filter panel (P13).
        "eu/kanade/tachiyomi/ui/browse/source/browse/AutoCompleteItem.kt",
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

    // Library grouping, selection and batch actions (P2): the cover helper the desktop copy LibraryActions.kt
    // (app-shared/src/desktopMain) uses, and the change-category and remove dialogs with their helpers. Desktop
    // counterpart in app-shared/src/desktopMain: CategoryExtensions (`visualName`; the Android file's two
    // `stringResource` imports are ambiguous on desktop).
    desktopCopy(
        "eu/kanade/core/preference/CheckboxState.kt",
        "eu/kanade/presentation/category/components/CategoryDialogs.kt",
        "eu/kanade/presentation/components/DownloadDropdownMenu.kt",
        "eu/kanade/presentation/components/DropdownMenu.kt",
        "eu/kanade/presentation/library/DeleteLibraryMangaDialog.kt",
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
    // Manga details: edit info (P10). MangaInfoEdits.kt holds the platform-neutral part of SY's edit-info dialog and
    // `MangaScreenModel.updateMangaInfo` (initial fields, status choices, reset tags, what a save stores), which Android's
    // screen model calls; it uses `isLocal` from `:source-local` (desktop-only dependency of `:app-shared`), hence a copy.
    desktopCopy(
        "eu/kanade/tachiyomi/ui/manga/MangaInfoEdits.kt",
    )

    // The global feed (P13): FeedItems.kt holds the platform-neutral part of Android's FeedScreenModel (row titles, the
    // sources to add a row for, a row's first page, the row limit) and of SourceFeedScreenModel (a source's feed rows and
    // their first pages), which both call; it uses the Android-only LocaleHelper (desktop counterpart in
    // app-shared/src/desktopMain), hence a copy. The add-search, add-to-source-feed and delete dialogs of Android's feeds
    // are shared.
    desktopCopy(
        "eu/kanade/tachiyomi/ui/browse/feed/FeedItems.kt",
    )
    // Bulk favourites (P14): Komikku's selection mode of browse results (duplicate check, category dialog, adding); it
    // uses desktopCopy sources (AddTracks, CoverCache, UpdateMangaFromRemote, MangaExtensions), hence a copy. Its members
    // are public (were `internal`, which the desktop app cannot call); the dialogs are the desktop's own.
    desktopCopy(
        "eu/kanade/tachiyomi/ui/browse/BulkFavoriteScreenModel.kt",
    )
    // App lock (P16): AppLockRules.kt holds the platform-neutral rules of Android's SecureActivityDelegate (SY's lock
    // times and days, "Lock when idle"), which Android's delegate and the desktop app lock both use.
    shared(
        "eu/kanade/tachiyomi/ui/base/delegate/AppLockRules.kt",
    )
    // Settings search (P15): SettingsSearch.kt holds the platform-neutral part of Android's SettingsSearchScreen (what a
    // query finds, the breadcrumbs) and PreferenceScreen (the highlighted item's index), which both call; it uses the
    // desktopCopy Preference.kt, hence a copy.
    desktopCopy(
        "eu/kanade/presentation/more/settings/screen/SettingsSearch.kt",
    )
    shared(
        "eu/kanade/presentation/browse/FeedAddSearchDialog.kt",
        "eu/kanade/presentation/browse/components/SourceFeedAddDialog.kt",
        "eu/kanade/presentation/browse/components/SourceFeedDeleteDialog.kt",
    )
    // Source migration (P18): Mihon's / Komikku's migration of one entry (MigrateMangaUseCase: chapters' read state and
    // history, categories, tracks, custom cover, notes, downloads, extras by MigrationFlag), the labels of the flags and
    // the "what to include" dialog with Copy / Migrate (its composable is public for the desktop, was `internal`). They
    // use desktopCopy sources (CoverCache, DownloadManager, UpdateMangaFromRemote, MangaExtensions), hence copies.
    desktopCopy(
        "mihon/domain/migration/usecases/MigrateMangaUseCase.kt",
        "mihon/feature/common/utils/MigrationFlag.kt",
        "mihon/feature/migration/dialog/MigrateMangaDialog.kt",
    )
    // Migration list (P19): Mihon's / Komikku's configuration (the sources and their order: MigrationConfigScreenModel.kt,
    // moved out of MigrationConfigScreen; the options sheet), the list's screen model with its automatic matching (smart
    // search engines, SY's throttle) and its dialogs. They use desktopCopy sources (MigrateMangaUseCase,
    // UpdateMangaFromRemote, LocaleHelper), hence copies. Desktop counterparts in app-shared/src/desktopMain: AdaptiveSheet
    // (the sheet as a centred dialog) and `getNameForMangaInfo` (DesktopSourceExtensions.kt). The screens are the desktop's
    // own (Android's use R drawables, the reorderable list and the Android app bar).
    desktopCopy(
        "eu/kanade/presentation/util/ChapterNumberFormatter.kt",
        "exh/util/ThrottleManager.kt",
        "mihon/feature/migration/config/MigrationConfigScreenModel.kt",
        "mihon/feature/migration/config/MigrationConfigScreenSheet.kt",
        "mihon/feature/migration/list/MigrationListScreenModel.kt",
        "mihon/feature/migration/list/components/MigrationExitDialog.kt",
        "mihon/feature/migration/list/components/MigrationMangaDialog.kt",
        "mihon/feature/migration/list/components/MigrationProgressDialog.kt",
        "mihon/feature/migration/list/models/MigratingManga.kt",
        "mihon/feature/migration/list/search/BaseSmartSearchEngine.kt",
        "mihon/feature/migration/list/search/SmartSourceSearchEngine.kt",
    )
    // Browse → Migrate (P20): the sources with library entries and their counts (sorted by Android's migration sorting
    // preferences) and a source's library entries with their selection. The entries' screen model compiles for both;
    // GetSourcesWithFavoriteCount uses `isLocal` (source-local, not a dependency of app-shared's Android target) and the
    // sources' screen model the Android-only `installedExtension` (Source.kt, drawables) and `SEARCH_DEBOUNCE_MILLIS`
    // (AppBar.kt), whose desktop counterparts are in app-shared/src/desktopMain, hence copies.
    shared(
        "eu/kanade/tachiyomi/ui/browse/migration/manga/MigrateMangaScreenModel.kt",
    )
    desktopCopy(
        "eu/kanade/domain/source/interactor/GetSourcesWithFavoriteCount.kt",
        "eu/kanade/tachiyomi/ui/browse/migration/sources/MigrateSourceScreenModel.kt",
    )
    // SY source categories (P21): the categories of the sources tab (create, rename, delete; a source in several) and their
    // screen model; preferences only, so they compile for both.
    shared(
        "eu/kanade/domain/source/interactor/CreateSourceCategory.kt",
        "eu/kanade/domain/source/interactor/DeleteSourceCategory.kt",
        "eu/kanade/domain/source/interactor/GetSourceCategories.kt",
        "eu/kanade/domain/source/interactor/RenameSourceCategory.kt",
        "eu/kanade/domain/source/interactor/SetSourceCategories.kt",
        "eu/kanade/tachiyomi/ui/category/sources/SourceCategoryScreenModel.kt",
    )
    // Extension info (P22): the extension's sources with their switches, incognito, uninstall, clear cookies. The toggle
    // is preferences only; the screen model uses app's ExtensionManager and LocaleHelper (desktop counterparts in
    // app-shared/src/desktopMain), hence a copy.
    shared(
        "eu/kanade/domain/source/interactor/ToggleIncognito.kt",
    )
    desktopCopy(
        "eu/kanade/tachiyomi/ui/browse/extension/details/ExtensionDetailsScreenModel.kt",
    )
    // The host API extensions call (MangaDex's headers use it): version and supported image types. It uses the generated
    // BuildConfig, hence a copy; desktop counterpart in app-shared/src/desktopMain: BuildConfig.
    desktopCopy(
        "eu/kanade/tachiyomi/AppInfo.kt",
    )
}

extra["borrowedAppSources.shared"] = shared.toList()
extra["borrowedAppSources.androidOnly"] = androidOnly.toList()
extra["borrowedAppSources.perPlatform"] = perPlatform.toList()
extra["borrowedAppSources.desktopCopy"] = desktopCopy.toList()
