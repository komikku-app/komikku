package eu.kanade.tachiyomi.data.preference

/**
 * This class stores the keys for the preferences in the application.
 */
object PreferenceKeys {

    const val themeMode = "pref_theme_mode_key"

    const val themeLight = "pref_theme_light_key"

    const val themeDark = "pref_theme_dark_key"

    const val confirmExit = "pref_confirm_exit"

    const val hideBottomBar = "pref_hide_bottom_bar_on_scroll"

    const val rotation = "pref_rotation_type_key"

    const val enableTransitionsPager = "pref_enable_transitions_pager_key"

    const val enableTransitionsWebtoon = "pref_enable_transitions_webtoon_key"

    const val doubleTapAnimationSpeed = "pref_double_tap_anim_speed"

    const val showPageNumber = "pref_show_page_number_key"

    const val dualPageSplitPaged = "pref_dual_page_split"

    const val dualPageSplitWebtoon = "pref_dual_page_split_webtoon"

    const val dualPageInvertPaged = "pref_dual_page_invert"

    const val dualPageInvertWebtoon = "pref_dual_page_invert_webtoon"

    const val showReadingMode = "pref_show_reading_mode"

    const val trueColor = "pref_true_color_key"

    const val fullscreen = "fullscreen"

    const val cutoutShort = "cutout_short"

    const val keepScreenOn = "pref_keep_screen_on_key"

    const val customBrightness = "pref_custom_brightness_key"

    const val customBrightnessValue = "custom_brightness_value"

    const val colorFilter = "pref_color_filter_key"

    const val colorFilterValue = "color_filter_value"

    const val colorFilterMode = "color_filter_mode"

    const val defaultViewer = "pref_default_viewer_key"

    const val imageScaleType = "pref_image_scale_type_key"

    const val zoomStart = "pref_zoom_start_key"

    const val readerTheme = "pref_reader_theme_key"

    const val cropBorders = "crop_borders"

    const val cropBordersWebtoon = "crop_borders_webtoon"

    const val readWithTapping = "reader_tap"

    const val pagerNavInverted = "reader_tapping_inverted"

    const val webtoonNavInverted = "reader_tapping_inverted_webtoon"

    const val readWithLongTap = "reader_long_tap"

    const val readWithVolumeKeys = "reader_volume_keys"

    const val readWithVolumeKeysInverted = "reader_volume_keys_inverted"

    const val navigationModePager = "reader_navigation_mode_pager"

    const val navigationModeWebtoon = "reader_navigation_mode_webtoon"

    const val webtoonSidePadding = "webtoon_side_padding"

    const val portraitColumns = "pref_library_columns_portrait_key"

    const val landscapeColumns = "pref_library_columns_landscape_key"

    const val jumpToChapters = "jump_to_chapters"

    const val updateOnlyNonCompleted = "pref_update_only_non_completed_key"

    const val autoUpdateTrack = "pref_auto_update_manga_sync_key"

    const val lastUsedSource = "last_catalogue_source"

    const val lastUsedCategory = "last_used_category"

    const val sourceDisplayMode = "pref_display_mode_catalogue"

    const val enabledLanguages = "source_languages"

    const val backupDirectory = "backup_directory"

    const val downloadsDirectory = "download_directory"

    const val downloadOnlyOverWifi = "pref_download_only_over_wifi_key"

    const val numberOfBackups = "backup_slots"

    const val backupInterval = "backup_interval"

    const val removeAfterReadSlots = "remove_after_read_slots"

    const val removeAfterMarkedAsRead = "pref_remove_after_marked_as_read_key"

    const val removeBookmarkedChapters = "pref_remove_bookmarked"

    const val libraryUpdateInterval = "pref_library_update_interval_key"

    const val libraryUpdateRestriction = "library_update_restriction"

    const val libraryUpdateCategories = "library_update_categories"

    const val libraryUpdatePrioritization = "library_update_prioritization"

    const val downloadedOnly = "pref_downloaded_only"

    const val filterDownloaded = "pref_filter_library_downloaded"

    const val filterUnread = "pref_filter_library_unread"

    const val filterCompleted = "pref_filter_library_completed"

    const val filterTracked = "pref_filter_library_tracked"

    const val filterStarted = "pref_filter_library_started"

    const val filterLewd = "pref_filter_library_lewd"

    const val librarySortingMode = "library_sorting_mode"

    const val automaticExtUpdates = "automatic_ext_updates"

    const val showNsfwSource = "show_nsfw_source"
    const val showNsfwExtension = "show_nsfw_extension"
    const val labelNsfwExtension = "label_nsfw_extension"

    const val startScreen = "start_screen"

    const val useBiometricLock = "use_biometric_lock"

    const val lockAppAfter = "lock_app_after"

    const val lastAppUnlock = "last_app_unlock"

    const val secureScreen = "secure_screen"

    const val hideNotificationContent = "hide_notification_content"

    const val autoUpdateMetadata = "auto_update_metadata"

    const val showLibraryUpdateErrors = "show_library_update_errors"

    const val downloadNew = "download_new"

    const val downloadNewCategories = "download_new_categories"

    const val libraryDisplayMode = "pref_display_mode_library"

    const val lang = "app_language"

    const val dateFormat = "app_date_format"

    const val defaultCategory = "default_category"

    const val skipRead = "skip_read"

    const val skipFiltered = "skip_filtered"

    const val downloadBadge = "display_download_badge"

    const val unreadBadge = "display_unread_badge"

    const val categoryTabs = "display_category_tabs"

    const val categoryNumberOfItems = "display_number_of_items"

    const val alwaysShowChapterTransition = "always_show_chapter_transition"

    const val searchPinnedSourcesOnly = "search_pinned_sources_only"

    const val enableDoh = "enable_doh"

    const val defaultChapterFilterByRead = "default_chapter_filter_by_read"

    const val defaultChapterFilterByDownloaded = "default_chapter_filter_by_downloaded"

    const val defaultChapterFilterByBookmarked = "default_chapter_filter_by_bookmarked"

    const val defaultChapterSortBySourceOrNumber = "default_chapter_sort_by_source_or_number" // and upload date

    const val defaultChapterSortByAscendingOrDescending = "default_chapter_sort_by_ascending_or_descending"

    const val defaultChapterDisplayByNameOrNumber = "default_chapter_display_by_name_or_number"

    const val incognitoMode = "incognito_mode"

    const val createLegacyBackup = "create_legacy_backup"

    fun trackUsername(syncId: Int) = "pref_mangasync_username_$syncId"

    fun trackPassword(syncId: Int) = "pref_mangasync_password_$syncId"

    fun trackToken(syncId: Int) = "track_token_$syncId"

    const val skipPreMigration = "skip_pre_migration"

    const val eh_showSyncIntro = "eh_show_sync_intro"

    const val eh_readOnlySync = "eh_sync_read_only"

    const val eh_lenientSync = "eh_lenient_sync"

    const val eh_useOrigImages = "eh_useOrigImages"

    const val eh_ehSettingsProfile = "eh_ehSettingsProfile"

    const val eh_exhSettingsProfile = "eh_exhSettingsProfile"

    const val eh_settingsKey = "eh_settingsKey"

    const val eh_sessionCookie = "eh_sessionCookie"

    const val eh_hathPerksCookie = "eh_hathPerksCookie"

    const val eh_enableExHentai = "enable_exhentai"

    const val eh_showSettingsUploadWarning = "eh_showSettingsUploadWarning2"

    const val eh_expandFilters = "eh_expand_filters"

    const val eh_readerThreads = "eh_reader_threads"

    const val eh_readerInstantRetry = "eh_reader_instant_retry"

    const val eh_utilAutoscrollInterval = "eh_util_autoscroll_interval"

    const val eh_cacheSize = "eh_cache_size"

    const val eh_preserveReadingPosition = "eh_preserve_reading_position"

    const val eh_autoSolveCaptchas = "eh_autosolve_captchas"

    const val eh_delegateSources = "eh_delegate_sources"

    const val eh_logLevel = "eh_log_level"

    const val eh_enableSourceBlacklist = "eh_enable_source_blacklist"

    const val eh_autoUpdateFrequency = "eh_auto_update_frequency"

    const val eh_autoUpdateRestrictions = "eh_auto_update_restrictions"

    const val eh_autoUpdateStats = "eh_auto_update_stats"

    const val eh_aggressivePageLoading = "eh_aggressive_page_loading"

    const val eh_preload_size = "eh_preload_size"

    const val eh_tag_filtering_value = "eh_tag_filtering_value"

    const val eh_tag_watching_value = "eh_tag_watching_value"

    const val eh_is_hentai_enabled = "eh_is_hentai_enabled"

    const val eh_use_auto_webtoon = "eh_use_auto_webtoon"

    const val eh_watched_list_default_state = "eh_watched_list_default_state"

    const val eh_settings_languages = "eh_settings_languages"

    const val eh_enabled_categories = "eh_enabled_categories"

    const val eh_ehentai_quality = "ehentai_quality"

    const val eh_enable_hah = "eh_enable_hah"

    const val latest_tab_sources = "latest_tab_sources"

    const val latest_tab_position = "latest_tab_position"

    const val sources_tab_categories = "sources_tab_categories"

    const val sources_tab_categories_filter = "sources_tab_categories_filter"

    const val sources_tab_source_categories = "sources_tab_source_categories"

    const val sourcesSort = "sources_sort"

    const val recommendsInOverflow = "recommends_in_overflow"

    const val enhancedEHentaiView = "enhanced_e_hentai_view"

    const val webtoonEnableZoomOut = "webtoon_enable_zoom_out"

    const val startReadingButton = "start_reading_button"

    const val groupLibraryBy = "group_library_by"

    const val continuousVerticalTappingByPage = "continuous_vertical_tapping_by_page"

    const val groupLibraryUpdateType = "group_library_update_type"

    const val useNewSourceNavigation = "use_new_source_navigation"

    const val mangaDexForceLatestCovers = "manga_dex_force_latest_covers"

    const val mangadexSimilarEnabled = "pref_related_show_tab_key"

    const val mangadexSimilarUpdateInterval = "related_update_interval"

    const val mangadexSimilarOnlyOverWifi = "pref_simular_only_over_wifi_key"

    const val preferredMangaDexId = "preferred_mangaDex_id"

    const val dataSaver = "data_saver"

    const val ignoreJpeg = "ignore_jpeg"

    const val ignoreGif = "ignore_gif"

    const val dataSaverImageQuality = "data_saver_image_quality"

    const val dataSaverImageFormatJpeg = "data_saver_image_format_jpeg"

    const val dataSaverServer = "data_saver_server"

    const val dataSaverColorBW = "data_saver_color_bw"

    const val saveChaptersAsCBZ = "save_chapter_as_cbz"

    const val saveChaptersAsCBZLevel = "save_chapter_as_cbz_level"

    const val allowLocalSourceHiddenFolders = "allow_local_source_hidden_folders"

    const val biometricTimeRanges = "biometric_time_ranges"

    const val sortTagsForLibrary = "sort_tags_for_library"

    const val dontDeleteFromCategories = "dont_delete_from_categories"

    const val extensionRepos = "extension_repos"

    const val cropBordersContinuesVertical = "crop_borders_continues_vertical"

    const val landscapeVerticalSeekbar = "pref_show_vert_seekbar_landscape"

    const val leftVerticalSeekbar = "pref_left_handed_vertical_seekbar"
}
