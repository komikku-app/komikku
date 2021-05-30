package eu.kanade.tachiyomi.ui.reader

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.ProgressDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MotionEvent
import android.view.View.LAYER_TYPE_HARDWARE
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.RelativeLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.preference.toggle
import eu.kanade.tachiyomi.databinding.ReaderActivityBinding
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.activity.BaseRxActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.reader.ReaderPresenter.SetAsCoverResult.AddToLibraryFirst
import eu.kanade.tachiyomi.ui.reader.ReaderPresenter.SetAsCoverResult.Error
import eu.kanade.tachiyomi.ui.reader.ReaderPresenter.SetAsCoverResult.Success
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterDialog
import eu.kanade.tachiyomi.ui.reader.loader.HttpPageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsSheet
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.ui.reader.viewer.BaseViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.VerticalPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.storage.getUriCompat
import eu.kanade.tachiyomi.util.system.GLUtil
import eu.kanade.tachiyomi.util.system.hasDisplayCutout
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.defaultBar
import eu.kanade.tachiyomi.util.view.hideBar
import eu.kanade.tachiyomi.util.view.isDefaultBar
import eu.kanade.tachiyomi.util.view.popupMenu
import eu.kanade.tachiyomi.util.view.setTooltip
import eu.kanade.tachiyomi.util.view.showBar
import eu.kanade.tachiyomi.widget.listener.SimpleAnimationListener
import eu.kanade.tachiyomi.widget.listener.SimpleSeekBarListener
import exh.log.xLogE
import exh.source.isEhBasedSource
import exh.util.defaultReaderType
import exh.util.mangaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import nucleus.factory.RequiresPresenter
import reactivecircus.flowbinding.android.view.clicks
import reactivecircus.flowbinding.android.widget.checkedChanges
import reactivecircus.flowbinding.android.widget.textChanges
import timber.log.Timber
import uy.kohesive.injekt.injectLazy
import java.io.File
import kotlin.math.abs
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

/**
 * Activity containing the reader of Tachiyomi. This activity is mostly a container of the
 * viewers, to which calls from the presenter or UI events are delegated.
 */
@RequiresPresenter(ReaderPresenter::class)
class ReaderActivity : BaseRxActivity<ReaderActivityBinding, ReaderPresenter>() {

    companion object {
        fun newIntent(context: Context, manga: Manga, chapter: Chapter): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                putExtra("manga", manga.id)
                putExtra("chapter", chapter.id)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        const val SHIFT_DOUBLE_PAGES = "shiftingDoublePages"
        const val SHIFTED_PAGE_INDEX = "shiftedPageIndex"
        const val SHIFTED_CHAP_INDEX = "shiftedChapterIndex"
    }

    private val preferences: PreferencesHelper by injectLazy()

    /**
     * The maximum bitmap size supported by the device.
     */
    val maxBitmapSize by lazy { GLUtil.maxTextureSize }

    val hasCutout by lazy { hasDisplayCutout() }

    /**
     * Viewer used to display the pages (pager, webtoon, ...).
     */
    var viewer: BaseViewer? = null
        private set

    /**
     * Whether the menu is currently visible.
     */
    var menuVisible = false
        private set

    // SY -->
    private var ehUtilsVisible = false

    private val autoScrollFlow = MutableSharedFlow<Unit>()
    private var autoScrollJob: Job? = null
    private val sourceManager: SourceManager by injectLazy()

    private var lastShiftDoubleState: Boolean? = null
    private var indexPageToShift: Int? = null
    private var indexChapterToShift: Long? = null
    // SY <--

    /**
     * Configuration at reader level, like background color or forced orientation.
     */
    private var config: ReaderConfig? = null

    /**
     * Progress dialog used when switching chapters from the menu buttons.
     */
    @Suppress("DEPRECATION")
    private var progressDialog: ProgressDialog? = null

    private var menuToggleToast: Toast? = null

    private var readingModeToast: Toast? = null

    /**
     * Called when the activity is created. Initializes the presenter and configuration.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(
            when (preferences.readerTheme().get()) {
                0 -> R.style.Theme_Reader_Light
                2 -> R.style.Theme_Reader_Dark_Grey
                else -> R.style.Theme_Reader_Dark
            }
        )
        super.onCreate(savedInstanceState)

        binding = ReaderActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (presenter.needsInit()) {
            val manga = intent.extras!!.getLong("manga", -1)
            val chapter = intent.extras!!.getLong("chapter", -1)
            if (manga == -1L || chapter == -1L) {
                finish()
                return
            }
            NotificationReceiver.dismissNotification(this, manga.hashCode(), Notifications.ID_NEW_CHAPTERS)
            presenter.init(manga, chapter)
        }

        if (savedInstanceState != null) {
            menuVisible = savedInstanceState.getBoolean(::menuVisible.name)
            // --> EH
            ehUtilsVisible = savedInstanceState.getBoolean(::ehUtilsVisible.name)
            // <-- EH
            // SY -->
            lastShiftDoubleState = savedInstanceState.get(SHIFT_DOUBLE_PAGES) as? Boolean
            indexPageToShift = savedInstanceState.get(SHIFTED_PAGE_INDEX) as? Int
            indexChapterToShift = savedInstanceState.get(SHIFTED_CHAP_INDEX) as? Long
            // SY <--
        }

        config = ReaderConfig()
        initializeMenu()

        // Avoid status bar showing up on rotation
        window.decorView.setOnSystemUiVisibilityChangeListener {
            setMenuVisibility(menuVisible, animate = false)
        }

        // Finish when incognito mode is disabled
        preferences.incognitoMode().asFlow()
            .drop(1)
            .onEach { if (!it) finish() }
            .launchIn(lifecycleScope)
    }

    // SY -->
    private fun setEhUtilsVisibility(visible: Boolean) {
        if (visible) {
            binding.ehUtils.isVisible = true
            binding.expandEhButton.setImageResource(R.drawable.ic_keyboard_arrow_up_white_32dp)
        } else {
            binding.ehUtils.isVisible = false
            binding.expandEhButton.setImageResource(R.drawable.ic_keyboard_arrow_down_white_32dp)
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun setupAutoscroll(interval: Double) {
        autoScrollJob?.cancel()
        if (interval == -1.0) return

        val duration = interval.seconds
        autoScrollJob = lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                delay(duration)
                autoScrollFlow.emit(Unit)
            }
        }
    }
    // SY <--

    /**
     * Called when the activity is destroyed. Cleans up the viewer, configuration and any view.
     */
    override fun onDestroy() {
        super.onDestroy()
        viewer?.destroy()
        viewer = null
        config = null
        menuToggleToast?.cancel()
        readingModeToast?.cancel()
        progressDialog?.dismiss()
        progressDialog = null
        // SY -->
        autoScrollJob?.cancel()
        autoScrollJob = null
        // SY <--
    }

    /**
     * Called when the activity is saving instance state. Current progress is persisted if this
     * activity isn't changing configurations.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(::menuVisible.name, menuVisible)
        // EXH -->
        outState.putBoolean(::ehUtilsVisible.name, ehUtilsVisible)
        // EXH <--
        // SY -->
        (viewer as? PagerViewer)?.let { pViewer ->
            val config = pViewer.config
            outState.putBoolean(SHIFT_DOUBLE_PAGES, config.shiftDoublePage)
            if (config.shiftDoublePage && config.doublePages) {
                pViewer.getShiftedPage()?.let {
                    outState.putInt(SHIFTED_PAGE_INDEX, it.index)
                    outState.putLong(SHIFTED_CHAP_INDEX, it.chapter.chapter.id ?: 0L)
                }
            }
        }
        // SY <--
        if (!isChangingConfigurations) {
            presenter.onSaveInstanceStateNonConfigurationChange()
        }
        super.onSaveInstanceState(outState)
    }

    /**
     * Set menu visibility again on activity resume to apply immersive mode again if needed.
     * Helps with rotations.
     */
    override fun onResume() {
        super.onResume()
        setMenuVisibility(menuVisible, animate = false)
    }

    /**
     * Called when the window focus changes. It sets the menu visibility to the last known state
     * to apply immersive mode again if needed.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setMenuVisibility(menuVisible, animate = false)
        }
    }

    /**
     * Called when the options menu of the toolbar is being created. It adds our custom menu.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.reader, menu)

        /*val isChapterBookmarked = presenter?.getCurrentChapter()?.chapter?.bookmark ?: false
        menu.findItem(R.id.action_bookmark).isVisible = !isChapterBookmarked
        menu.findItem(R.id.action_remove_bookmark).isVisible = isChapterBookmarked*/

        return true
    }

    /**
     * Called when an item of the options menu was clicked. Used to handle clicks on our menu
     * entries.
     */
    /*override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_bookmark -> {
                presenter.bookmarkCurrentChapter(true)
                invalidateOptionsMenu()
            }
            R.id.action_remove_bookmark -> {
                presenter.bookmarkCurrentChapter(false)
                invalidateOptionsMenu()
            }
        }
        return super.onOptionsItemSelected(item)
    }*/

    /**
     * Called when the user clicks the back key or the button on the toolbar. The call is
     * delegated to the presenter.
     */
    override fun onBackPressed() {
        presenter.onBackPressed()
        super.onBackPressed()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_N) {
            presenter.loadNextChapter()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_P) {
            presenter.loadPreviousChapter()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Dispatches a key event. If the viewer doesn't handle it, call the default implementation.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = viewer?.handleKeyEvent(event) ?: false
        return handled || super.dispatchKeyEvent(event)
    }

    /**
     * Dispatches a generic motion event. If the viewer doesn't handle it, call the default
     * implementation.
     */
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val handled = viewer?.handleGenericMotionEvent(event) ?: false
        return handled || super.dispatchGenericMotionEvent(event)
    }

    /**
     * Initializes the reader menu. It sets up click listeners and the initial visibility.
     */
    private fun initializeMenu() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.readerMenu) { _, insets ->
            if (!window.isDefaultBar()) {
                val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                binding.readerMenu.setPadding(
                    systemInsets.left,
                    systemInsets.top,
                    systemInsets.right,
                    systemInsets.bottom
                )
            }
            insets
        }

        binding.toolbar.setOnClickListener {
            presenter.manga?.id?.let { id ->
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        action = MainActivity.SHORTCUT_MANGA
                        putExtra(MangaController.MANGA_EXTRA, id)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                )
            }
        }

        // SY -->
        // Init listeners on bottom menu
        val listener = object : SimpleSeekBarListener() {
            override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                if (viewer != null && fromUser) {
                    moveToPageIndex(value)
                }
            }
        }
        listOf(binding.pageSeekbar, binding.pageSeekbarVert)
            .forEach {
                it.setOnSeekBarChangeListener(listener)
            }
        // SY <--

        // Extra menu buttons

        // SY -->
        listOf(binding.leftChapter, binding.aboveChapter).forEach {
            it.clicks()
                .onEach {
                    if (viewer != null) {
                        if (viewer is R2LPagerViewer) {
                            loadNextChapter()
                        } else {
                            loadPreviousChapter()
                        }
                    }
                }
                .launchIn(lifecycleScope)
        }
        listOf(binding.rightChapter, binding.belowChapter).forEach {
            it.clicks()
                .onEach {
                    if (viewer != null) {
                        if (viewer is R2LPagerViewer) {
                            loadPreviousChapter()
                        } else {
                            loadNextChapter()
                        }
                    }
                }
                .launchIn(lifecycleScope)
        }
        // SY <--

        binding.actionSettings.setOnClickListener {
            ReaderSettingsSheet(this).show()
        }

        // Reading mode
        with(binding.actionReadingMode) {
            setTooltip(R.string.viewer)

            setOnClickListener {
                popupMenu(
                    items = ReadingModeType.values().map { it.flagValue to it.stringRes },
                    selectedItemId = presenter.getMangaReadingMode(resolveDefault = false),
                ) {
                    val newReadingMode = ReadingModeType.fromPreference(itemId)

                    presenter.setMangaReadingMode(newReadingMode.flagValue)

                    menuToggleToast?.cancel()
                    if (!preferences.showReadingMode()) {
                        menuToggleToast = toast(newReadingMode.stringRes)
                    }
                }
            }
        }

        // Crop borders
        with(binding.actionCropBorders) {
            setTooltip(R.string.pref_crop_borders)

            setOnClickListener {
                // SY -->
                val mangaViewer = presenter.getMangaReadingMode()
                // SY <--
                val isPagerType = ReadingModeType.isPagerType(mangaViewer)
                val enabled = if (isPagerType) {
                    preferences.cropBorders().toggle()
                } else {
                    // SY -->
                    if (ReadingModeType.fromPreference(mangaViewer) == ReadingModeType.CONTINUOUS_VERTICAL) {
                        preferences.cropBordersContinuousVertical().toggle()
                    } else {
                        preferences.cropBordersWebtoon().toggle()
                    }
                    // SY <--
                }

                menuToggleToast?.cancel()
                menuToggleToast = toast(
                    if (enabled) {
                        R.string.on
                    } else {
                        R.string.off
                    }
                )
            }
        }
        updateCropBordersShortcut()
        listOf(preferences.cropBorders(), preferences.cropBordersWebtoon() /* SY --> */, preferences.cropBordersContinuousVertical()/* SY <-- */)
            .forEach { pref ->
                pref.asFlow()
                    .onEach { updateCropBordersShortcut() }
                    .launchIn(lifecycleScope)
            }

        // Rotation
        with(binding.actionRotation) {
            setTooltip(R.string.rotation_type)

            setOnClickListener {
                popupMenu(
                    items = OrientationType.values().map { it.flagValue to it.stringRes },
                    selectedItemId = presenter.manga?.orientationType
                        ?: preferences.defaultOrientationType(),
                ) {
                    val newOrientation = OrientationType.fromPreference(itemId)

                    presenter.setMangaOrientationType(newOrientation.flagValue)

                    updateOrientationShortcut(newOrientation.flagValue)

                    menuToggleToast?.cancel()
                    menuToggleToast = toast(newOrientation.stringRes)
                }
            }
        }

        // Settings sheet
        with(binding.actionSettings) {
            setTooltip(R.string.action_settings)

            setOnClickListener {
                ReaderSettingsSheet(this@ReaderActivity).show()
            }

            setOnLongClickListener {
                ReaderSettingsSheet(this@ReaderActivity, showColorFilterSettings = true).show()
                true
            }
        }

        // --> EH
        with(binding.actionWebView) {
            setTooltip(R.string.action_open_in_web_view)

            setOnClickListener {
                openMangaInBrowser()
            }
        }

        with(binding.actionChapterList) {
            setTooltip(R.string.chapters)

            setOnClickListener {
                ReaderChapterDialog(this@ReaderActivity)
            }
        }

        with(binding.doublePage) {
            setTooltip(R.string.page_layout)

            setOnClickListener {
                if (preferences.pageLayout().get() == PagerConfig.PageLayout.AUTOMATIC) {
                    (viewer as? PagerViewer)?.config?.let { config ->
                        config.doublePages = !config.doublePages
                        reloadChapters(config.doublePages, true)
                    }
                    updateBottomButtons()
                } else {
                    preferences.pageLayout().set(1 - preferences.pageLayout().get())
                }
            }
        }

        with(binding.shiftPageButton) {
            setTooltip(R.string.shift_double_pages)

            setOnClickListener {
                shiftDoublePages()
            }
        }

        binding.expandEhButton.clicks()
            .onEach {
                ehUtilsVisible = !ehUtilsVisible
                setEhUtilsVisibility(ehUtilsVisible)
            }
            .launchIn(lifecycleScope)

        binding.ehAutoscrollFreq.setText(
            preferences.autoscrollInterval().get().let {
                if (it == -1f) {
                    ""
                } else {
                    it.toString()
                }
            }
        )

        binding.ehAutoscroll.checkedChanges()
            .onEach {
                setupAutoscroll(
                    if (it) {
                        preferences.autoscrollInterval().get().toDouble()
                    } else {
                        -1.0
                    }
                )
            }
            .launchIn(lifecycleScope)

        binding.ehAutoscrollFreq.textChanges()
            .onEach {
                val parsed = it.toString().toDoubleOrNull()

                if (parsed == null || parsed <= 0 || parsed > 9999) {
                    binding.ehAutoscrollFreq.error = "Invalid frequency"
                    preferences.autoscrollInterval().set(-1f)
                    binding.ehAutoscroll.isEnabled = false
                    setupAutoscroll(-1.0)
                } else {
                    binding.ehAutoscrollFreq.error = null
                    preferences.autoscrollInterval().set(parsed.toFloat())
                    binding.ehAutoscroll.isEnabled = true
                    setupAutoscroll(if (binding.ehAutoscroll.isChecked) parsed else -1.0)
                }
            }
            .launchIn(lifecycleScope)

        binding.ehAutoscrollHelp.clicks()
            .onEach {
                MaterialDialog(this)
                    .title(R.string.eh_autoscroll_help)
                    .message(R.string.eh_autoscroll_help_message)
                    .positiveButton(android.R.string.ok)
                    .show()
            }
            .launchIn(lifecycleScope)

        binding.ehRetryAll.clicks()
            .onEach {
                var retried = 0

                presenter.viewerChaptersRelay.value
                    .currChapter
                    .pages
                    ?.forEachIndexed { _, page ->
                        var shouldQueuePage = false
                        if (page.status == Page.ERROR) {
                            shouldQueuePage = true
                        } /*else if (page.status == Page.LOAD_PAGE ||
                                    page.status == Page.DOWNLOAD_IMAGE) {
                                // Do nothing
                            }*/

                        if (shouldQueuePage) {
                            page.status = Page.QUEUE
                        } else {
                            return@forEachIndexed
                        }

                        // If we are using EHentai/ExHentai, get a new image URL
                        presenter.manga?.let { m ->
                            val src = sourceManager.get(m.source)
                            if (src?.isEhBasedSource() == true) {
                                page.imageUrl = null
                            }
                        }

                        val loader = page.chapter.pageLoader
                        if (page.index == exhCurrentpage()?.index && loader is HttpPageLoader) {
                            loader.boostPage(page)
                        } else {
                            loader?.retryPage(page)
                        }

                        retried++
                    }

                toast("Retrying $retried failed pages...")
            }
            .launchIn(lifecycleScope)

        binding.ehRetryAllHelp.clicks()
            .onEach {
                MaterialDialog(this)
                    .title(R.string.eh_retry_all_help)
                    .message(R.string.eh_retry_all_help_message)
                    .positiveButton(android.R.string.ok)
                    .show()
            }
            .launchIn(lifecycleScope)

        binding.ehBoostPage.clicks()
            .onEach {
                viewer?.let { _ ->
                    val curPage = exhCurrentpage() ?: run {
                        toast("This page cannot be boosted (invalid page)!")
                        return@let
                    }

                    if (curPage.status == Page.ERROR) {
                        toast("Page failed to load, press the retry button instead!")
                    } else if (curPage.status == Page.LOAD_PAGE || curPage.status == Page.DOWNLOAD_IMAGE) {
                        toast("This page is already downloading!")
                    } else if (curPage.status == Page.READY) {
                        toast("This page has already been downloaded!")
                    } else {
                        val loader = (presenter.viewerChaptersRelay.value.currChapter.pageLoader as? HttpPageLoader)
                        if (loader != null) {
                            loader.boostPage(curPage)
                            toast("Boosted current page!")
                        } else {
                            toast("This page cannot be boosted (invalid page loader)!")
                        }
                    }
                }
            }
            .launchIn(lifecycleScope)

        binding.ehBoostPageHelp.clicks()
            .onEach {
                MaterialDialog(this)
                    .title(R.string.eh_boost_page_help)
                    .message(R.string.eh_boost_page_help_message)
                    .positiveButton(android.R.string.ok)
                    .show()
            }
            .launchIn(lifecycleScope)

        autoScrollFlow
            .onEach {
                viewer.let { v ->
                    if (v is PagerViewer) v.moveToNext()
                    else if (v is WebtoonViewer) v.scrollDown()
                }
            }
            .launchIn(lifecycleScope)

        updateBottomButtons()
        // <-- EH

        // Set initial visibility
        setMenuVisibility(menuVisible)

        // --> EH
        setEhUtilsVisibility(ehUtilsVisible)
        // <-- EH
    }

    // EXH -->
    private fun exhCurrentpage(): ReaderPage? {
        val currentPage = (((viewer as? PagerViewer)?.currentPage ?: (viewer as? WebtoonViewer)?.currentPage) as? ReaderPage)?.index
        return currentPage?.let { presenter.viewerChaptersRelay.value.currChapter.pages?.getOrNull(it) }
    }

    fun updateBottomButtons() {
        val enabledButtons = preferences.readerBottomButtons().get()
        with(binding) {
            actionReadingMode.isVisible = ReaderBottomButton.ReadingMode.isIn(enabledButtons)
            actionRotation.isVisible =
                ReaderBottomButton.Rotation.isIn(enabledButtons)
            doublePage.isVisible =
                viewer is PagerViewer && ReaderBottomButton.PageLayout.isIn(enabledButtons) && !preferences.dualPageSplitPaged().get()
            actionCropBorders.isVisible =
                if (viewer is PagerViewer) {
                    ReaderBottomButton.CropBordersPager.isIn(enabledButtons)
                } else {
                    val continuous = (viewer as? WebtoonViewer)?.isContinuous ?: false
                    if (continuous) {
                        ReaderBottomButton.CropBordersWebtoon.isIn(enabledButtons)
                    } else {
                        ReaderBottomButton.CropBordersContinuesVertical.isIn(enabledButtons)
                    }
                }
            actionWebView.isVisible =
                ReaderBottomButton.WebView.isIn(enabledButtons)
            actionChapterList.isVisible =
                ReaderBottomButton.ViewChapters.isIn(enabledButtons)
            shiftPageButton.isVisible = (viewer as? PagerViewer)?.config?.doublePages ?: false
        }
    }

    fun reloadChapters(doublePages: Boolean, force: Boolean = false) {
        val pViewer = viewer as? PagerViewer ?: return
        pViewer.updateShifting()
        if (!force && pViewer.config.autoDoublePages) {
            setDoublePageMode(pViewer)
        } else {
            pViewer.config.doublePages = doublePages
        }
        val currentChapter = presenter.getCurrentChapter()
        if (doublePages) {
            // If we're moving from singe to double, we want the current page to be the first page
            pViewer.config.shiftDoublePage = (
                binding.pageSeekbar.progress +
                    (currentChapter?.pages?.take(binding.pageSeekbar.progress)?.count { it.fullPage || it.isolatedPage } ?: 0)
                ) % 2 != 0
        }
        presenter.viewerChaptersRelay.value?.let {
            pViewer.setChaptersDoubleShift(it)
        }
    }

    private fun setDoublePageMode(viewer: PagerViewer) {
        val currentOrientation = resources.configuration.orientation
        viewer.config.doublePages = currentOrientation == Configuration.ORIENTATION_LANDSCAPE
    }

    private fun shiftDoublePages() {
        (viewer as? PagerViewer)?.config?.let { config ->
            config.shiftDoublePage = !config.shiftDoublePage
            presenter.viewerChaptersRelay.value?.let {
                (viewer as? PagerViewer)?.updateShifting()
                (viewer as? PagerViewer)?.setChaptersDoubleShift(it)
                invalidateOptionsMenu()
            }
        }
    }
    // EXH <--

    private fun updateOrientationShortcut(preference: Int) {
        val orientation = OrientationType.fromPreference(preference)
        binding.actionRotation.setImageResource(orientation.iconRes)
    }

    private fun updateCropBordersShortcut() {
        val mangaViewer = presenter.getMangaReadingMode()
        val isPagerType = ReadingModeType.isPagerType(mangaViewer)
        val enabled = if (isPagerType) {
            preferences.cropBorders().get()
        } else {
            // SY -->
            if (ReadingModeType.fromPreference(mangaViewer) == ReadingModeType.CONTINUOUS_VERTICAL) {
                preferences.cropBordersContinuousVertical().get()
            } else {
                preferences.cropBordersWebtoon().get()
            }
            // SY <--
        }

        binding.actionCropBorders.setImageResource(
            if (enabled) {
                R.drawable.ic_crop_24dp
            } else {
                R.drawable.ic_crop_off_24dp
            }
        )
    }

    /**
     * Sets the visibility of the menu according to [visible] and with an optional parameter to
     * [animate] the views.
     */
    fun setMenuVisibility(visible: Boolean, animate: Boolean = true) {
        menuVisible = visible
        if (visible) {
            if (preferences.fullscreen().get()) {
                window.showBar()
            } else {
                resetDefaultMenuAndBar()
            }
            binding.readerMenu.isVisible = true

            if (animate) {
                val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.enter_from_top)
                toolbarAnimation.setAnimationListener(
                    object : SimpleAnimationListener() {
                        override fun onAnimationStart(animation: Animation) {
                            // Fix status bar being translucent the first time it's opened.
                            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                        }
                    }
                )
                // EXH -->
                binding.header.startAnimation(toolbarAnimation)
                // EXH <--

                val vertAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_side)
                val vertAnimationLeft = AnimationUtils.loadAnimation(this, R.anim.fade_in_side_left)
                if (preferences.leftVerticalSeekbar().get() && binding.readerNavVert.isVisible) {
                    binding.seekbarVertContainer.startAnimation(vertAnimationLeft)
                } else {
                    binding.seekbarVertContainer.startAnimation(vertAnimation)
                }

                val bottomAnimation = AnimationUtils.loadAnimation(this, R.anim.enter_from_bottom)
                binding.readerMenuBottom.startAnimation(bottomAnimation)
            }

            if (preferences.showPageNumber().get()) {
                config?.setPageNumberVisibility(false)
            }
        } else {
            if (preferences.fullscreen().get()) {
                window.hideBar()
            } else {
                resetDefaultMenuAndBar()
            }

            if (animate) {
                val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.exit_to_top)
                toolbarAnimation.setAnimationListener(
                    object : SimpleAnimationListener() {
                        override fun onAnimationEnd(animation: Animation) {
                            binding.readerMenu.isVisible = false
                        }
                    }
                )
                // EXH -->
                binding.header.startAnimation(toolbarAnimation)
                // EXH <--

                val vertAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_out_side)
                val vertAnimationLeft = AnimationUtils.loadAnimation(this, R.anim.fade_out_side_left)
                if (preferences.leftVerticalSeekbar().get() && binding.readerNavVert.isVisible) {
                    binding.seekbarVertContainer.startAnimation(vertAnimationLeft)
                } else {
                    binding.seekbarVertContainer.startAnimation(vertAnimation)
                }

                val bottomAnimation = AnimationUtils.loadAnimation(this, R.anim.exit_to_bottom)
                binding.readerMenuBottom.startAnimation(bottomAnimation)
            }

            if (preferences.showPageNumber().get()) {
                config?.setPageNumberVisibility(true)
            }
        }
    }

    // SY -->
    fun openMangaInBrowser() {
        val source = sourceManager.getOrStub(presenter.manga!!.source) as? HttpSource ?: return
        val url = try {
            source.mangaDetailsRequest(presenter.manga!!).url.toString()
        } catch (e: Exception) {
            return
        }

        val intent = WebViewActivity.newIntent(
            applicationContext,
            url,
            source.id,
            presenter.manga!!.title
        )
        startActivity(intent)
    }
    // SY <--

    /**
     * Reset menu padding and system bar
     */
    private fun resetDefaultMenuAndBar() {
        binding.readerMenu.setPadding(0)
        window.defaultBar()
    }

    /**
     * Called from the presenter when a manga is ready. Used to instantiate the appropriate viewer
     * and the toolbar title.
     */
    fun setManga(manga: Manga) {
        val prevViewer = viewer

        val viewerMode = ReadingModeType.fromPreference(presenter.getMangaReadingMode(resolveDefault = false))
        binding.actionReadingMode.setImageResource(viewerMode.iconRes)

        val newViewer = ReadingModeType.toViewer(presenter.getMangaReadingMode(), this)

        setOrientation(presenter.getMangaOrientationType())

        // Destroy previous viewer if there was one
        if (prevViewer != null) {
            prevViewer.destroy()
            binding.viewerContainer.removeAllViews()
        }
        viewer = newViewer
        binding.viewerContainer.addView(newViewer.getView())

        // SY -->
        if (newViewer is PagerViewer) {
            if (preferences.pageLayout().get() == PagerConfig.PageLayout.AUTOMATIC) {
                setDoublePageMode(newViewer)
            }
            lastShiftDoubleState?.let { newViewer.config.shiftDoublePage = it }
        }

        val defaultReaderType = manga.defaultReaderType(manga.mangaType(sourceName = sourceManager.get(manga.source)?.name))
        if (preferences.useAutoWebtoon().get() && manga.readingModeType == ReadingModeType.DEFAULT.flagValue && defaultReaderType != null && defaultReaderType == ReadingModeType.WEBTOON.prefValue) {
            readingModeToast?.cancel()
            readingModeToast = toast(resources.getString(R.string.eh_auto_webtoon_snack))
        } else if (preferences.showReadingMode()) {
            // SY <--
            showReadingModeToast(presenter.getMangaReadingMode())
        }

        // SY -->

        // --> Vertical seekbar hide on landscape

        if (
            !preferences.forceHorizontalSeekbar().get() &&
            (
                (
                    resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && preferences.landscapeVerticalSeekbar().get()
                    ) ||
                    resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                ) &&
            (viewer is WebtoonViewer || viewer is VerticalPagerViewer)
        ) {
            binding.readerNavVert.isVisible = true
            binding.readerNavHorz.isVisible = false
        } else {
            binding.readerNavVert.isVisible = false
            binding.readerNavHorz.isVisible = true
        }

        // <-- Vertical seekbar hide on landscape

        // --> Left-handed vertical seekbar

        val params = binding.readerNavVert.layoutParams as RelativeLayout.LayoutParams
        if (preferences.leftVerticalSeekbar().get() && binding.readerNavVert.isVisible) {
            params.removeRule(RelativeLayout.ALIGN_PARENT_END)
            binding.readerNavVert.layoutParams = params
        }

        // <-- Left-handed vertical seekbar

        updateBottomButtons()
        // SY <--
        binding.toolbar.title = manga.title

        binding.pageSeekbar.isRTL = newViewer is R2LPagerViewer
        if (newViewer is R2LPagerViewer) {
            binding.leftChapter.setTooltip(R.string.action_next_chapter)
            binding.rightChapter.setTooltip(R.string.action_previous_chapter)
        } else {
            binding.leftChapter.setTooltip(R.string.action_previous_chapter)
            binding.rightChapter.setTooltip(R.string.action_next_chapter)
        }

        binding.pleaseWait.isVisible = true
        binding.pleaseWait.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in_long))
    }

    private fun showReadingModeToast(mode: Int) {
        try {
            val strings = resources.getStringArray(R.array.viewers_selector)
            readingModeToast?.cancel()
            readingModeToast = toast(strings[mode])
        } catch (e: ArrayIndexOutOfBoundsException) {
            Timber.e("Unknown reading mode: $mode")
        }
    }

    /**
     * Called from the presenter whenever a new [viewerChapters] have been set. It delegates the
     * method to the current viewer, but also set the subtitle on the toolbar.
     */
    fun setChapters(viewerChapters: ViewerChapters) {
        binding.pleaseWait.isVisible = false
        // SY -->
        if (indexChapterToShift != null && indexPageToShift != null) {
            viewerChapters.currChapter.pages?.find { it.index == indexPageToShift && it.chapter.chapter.id == indexChapterToShift }?.let {
                (viewer as? PagerViewer)?.updateShifting(it)
            }
            indexChapterToShift = null
            indexPageToShift = null
        } else if (lastShiftDoubleState != null) {
            val currentChapter = viewerChapters.currChapter
            (viewer as? PagerViewer)?.config?.shiftDoublePage = (
                currentChapter.requestedPage +
                    (
                        currentChapter.pages?.take(currentChapter.requestedPage)
                            ?.count { it.fullPage || it.isolatedPage } ?: 0
                        )
                ) % 2 != 0
        }
        // SY <--

        viewer?.setChapters(viewerChapters)
        binding.toolbar.subtitle = viewerChapters.currChapter.chapter.name

        // Invalidate menu to show proper chapter bookmark state
        invalidateOptionsMenu()
    }

    /**
     * Called from the presenter if the initial load couldn't load the pages of the chapter. In
     * this case the activity is closed and a toast is shown to the user.
     */
    fun setInitialChapterError(error: Throwable) {
        Timber.e(error)
        finish()
        toast(error.message)
    }

    /**
     * Called from the presenter whenever it's loading the next or previous chapter. It shows or
     * dismisses a non-cancellable dialog to prevent user interaction according to the value of
     * [show]. This is only used when the next/previous buttons on the toolbar are clicked; the
     * other cases are handled with chapter transitions on the viewers and chapter preloading.
     */
    @Suppress("DEPRECATION")
    fun setProgressDialog(show: Boolean) {
        progressDialog?.dismiss()
        progressDialog = if (show) {
            ProgressDialog.show(this, null, getString(R.string.loading), true)
        } else {
            null
        }
    }

    /**
     * Moves the viewer to the given page [index]. It does nothing if the viewer is null or the
     * page is not found.
     */
    fun moveToPageIndex(index: Int) {
        val viewer = viewer ?: return
        val currentChapter = presenter.getCurrentChapter() ?: return
        val page = currentChapter.pages?.getOrNull(index) ?: return
        viewer.moveToPage(page)
    }

    /**
     * Tells the presenter to load the next chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    private fun loadNextChapter() {
        presenter.loadNextChapter()
    }

    /**
     * Tells the presenter to load the previous chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    private fun loadPreviousChapter() {
        presenter.loadPreviousChapter()
    }

    /**
     * Called from the viewer whenever a [page] is marked as active. It updates the values of the
     * bottom menu and delegates the change to the presenter.
     */
    @SuppressLint("SetTextI18n")
    fun onPageSelected(page: ReaderPage, hasExtraPage: Boolean = false) {
        val newChapter = presenter.onPageSelected(page)
        val pages = page.chapter.pages ?: return

        val currentPage = if (hasExtraPage) {
            if (resources.isLTR) "${page.number}-${page.number + 1}" else "${page.number + 1}-${page.number}"
        } else {
            "${page.number}"
        }

        // Set bottom page number
        binding.pageNumber.text = "$currentPage/${pages.size}"
        // binding.pageText.text = "${page.number}/${pages.size}"

        // Set seekbar page number
        if (viewer !is R2LPagerViewer) {
            binding.leftPageText.text = currentPage
            binding.rightPageText.text = "${pages.size}"
        } else {
            binding.rightPageText.text = currentPage
            binding.leftPageText.text = "${pages.size}"
        }

        // SY -->
        binding.abovePageText.text = currentPage
        binding.belowPageText.text = "${pages.size}"
        // SY <--

        binding.pageSeekbar.max = pages.lastIndex
        binding.pageSeekbar.progress = page.index

        // SY -->
        binding.pageSeekbarVert.max = pages.lastIndex
        binding.pageSeekbarVert.progress = page.index
        // SY <--
    }

    /**
     * Called from the viewer whenever a [page] is long clicked. A bottom sheet with a list of
     * actions to perform is shown.
     */
    fun onPageLongTap(page: ReaderPage, extraPage: ReaderPage? = null) {
        // SY -->
        try {
            val viewer = viewer as? PagerViewer
            ReaderPageSheet(
                this,
                page,
                extraPage,
                (viewer !is R2LPagerViewer) xor (viewer?.config?.invertDoublePages ?: false),
                viewer?.config?.pageCanvasColor
            ).show()
        } catch (e: WindowManager.BadTokenException) {
            xLogE("Caught and ignoring reader page sheet launch exception!", e)
        }
        // SY <--
    }

    /**
     * Called from the viewer when the given [chapter] should be preloaded. It should be called when
     * the viewer is reaching the beginning or end of a chapter or the transition page is active.
     */
    fun requestPreloadChapter(chapter: ReaderChapter) {
        presenter.preloadChapter(chapter)
    }

    /**
     * Called from the viewer to toggle the visibility of the menu. It's implemented on the
     * viewer because each one implements its own touch and key events.
     */
    fun toggleMenu() {
        setMenuVisibility(!menuVisible)
    }

    /**
     * Called from the viewer to show the menu.
     */
    fun showMenu() {
        if (!menuVisible) {
            setMenuVisibility(true)
        }
    }

    /**
     * Called from the page sheet. It delegates the call to the presenter to do some IO, which
     * will call [onShareImageResult] with the path the image was saved on when it's ready.
     */
    fun shareImage(page: ReaderPage) {
        presenter.shareImage(page)
    }

    // SY -->
    fun shareImages(firstPage: ReaderPage, secondPage: ReaderPage, isLTR: Boolean, @ColorInt bg: Int) {
        presenter.shareImages(firstPage, secondPage, isLTR, bg)
    }
    // SY <--

    /**
     * Called from the presenter when a page is ready to be shared. It shows Android's default
     * sharing tool.
     */
    fun onShareImageResult(file: File, page: ReaderPage /* SY --> */, secondPage: ReaderPage? = null /* SY <-- */) {
        val manga = presenter.manga ?: return
        val chapter = page.chapter.chapter

        // SY -->
        val text = if (secondPage != null) {
            getString(R.string.share_pages_info, manga.title, chapter.name, if (resources.isLTR) "${page.number}-${page.number + 1}" else "${page.number + 1}-${page.number}")
        } else {
            getString(R.string.share_page_info, manga.title, chapter.name, page.number)
        }
        // SY <--

        val uri = file.getUriCompat(this)
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, /* SY --> */ text /* SY <-- */)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            type = "image/*"
        }
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
    }

    /**
     * Called from the page sheet. It delegates saving the image of the given [page] on external
     * storage to the presenter.
     */
    fun saveImage(page: ReaderPage) {
        presenter.saveImage(page)
    }

    // SY -->
    fun saveImages(firstPage: ReaderPage, secondPage: ReaderPage, isLTR: Boolean, @ColorInt bg: Int) {
        presenter.saveImages(firstPage, secondPage, isLTR, bg)
    }
    // SY <--

    /**
     * Called from the presenter when a page is saved or fails. It shows a message or logs the
     * event depending on the [result].
     */
    fun onSaveImageResult(result: ReaderPresenter.SaveImageResult) {
        when (result) {
            is ReaderPresenter.SaveImageResult.Success -> {
                toast(R.string.picture_saved)
            }
            is ReaderPresenter.SaveImageResult.Error -> {
                Timber.e(result.error)
            }
        }
    }

    /**
     * Called from the page sheet. It delegates setting the image of the given [page] as the
     * cover to the presenter.
     */
    fun setAsCover(page: ReaderPage) {
        presenter.setAsCover(page)
    }

    /**
     * Called from the presenter when a page is set as cover or fails. It shows a different message
     * depending on the [result].
     */
    fun onSetAsCoverResult(result: ReaderPresenter.SetAsCoverResult) {
        toast(
            when (result) {
                Success -> R.string.cover_updated
                AddToLibraryFirst -> R.string.notification_first_add_to_library
                Error -> R.string.notification_cover_update_failed
            }
        )
    }

    /**
     * Forces the user preferred [orientation] on the activity.
     */
    fun setOrientation(orientation: Int) {
        val newOrientation = OrientationType.fromPreference(orientation)
        if (newOrientation.flag != requestedOrientation) {
            requestedOrientation = newOrientation.flag
        }
    }

    /**
     * Class that handles the user preferences of the reader.
     */
    private inner class ReaderConfig {

        private val grayscalePaint by lazy {
            Paint().apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix().apply {
                        setSaturation(0f)
                    }
                )
            }
        }

        /**
         * Initializes the reader subscriptions.
         */
        init {
            preferences.readerTheme().asFlow()
                .drop(1) // We only care about updates
                .onEach { recreate() }
                .launchIn(lifecycleScope)

            preferences.showPageNumber().asFlow()
                .onEach { setPageNumberVisibility(it) }
                .launchIn(lifecycleScope)

            preferences.trueColor().asFlow()
                .onEach { setTrueColor(it) }
                .launchIn(lifecycleScope)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                preferences.cutoutShort().asFlow()
                    .onEach { setCutoutShort(it) }
                    .launchIn(lifecycleScope)
            }

            preferences.keepScreenOn().asFlow()
                .onEach { setKeepScreenOn(it) }
                .launchIn(lifecycleScope)

            preferences.customBrightness().asFlow()
                .onEach { setCustomBrightness(it) }
                .launchIn(lifecycleScope)

            preferences.colorFilter().asFlow()
                .onEach { setColorFilter(it) }
                .launchIn(lifecycleScope)

            preferences.colorFilterMode().asFlow()
                .onEach { setColorFilter(preferences.colorFilter().get()) }
                .launchIn(lifecycleScope)

            preferences.grayscale().asFlow()
                .onEach { setGrayscale(it) }
                .launchIn(lifecycleScope)

            preferences.pageLayout().asFlow()
                .drop(1)
                .onEach { updateBottomButtons() }
                .launchIn(lifecycleScope)

            preferences.dualPageSplitPaged().asFlow()
                .drop(1)
                .onEach {
                    if (viewer !is PagerViewer) return@onEach
                    updateBottomButtons()
                    reloadChapters(
                        !it && when (preferences.pageLayout().get()) {
                            PagerConfig.PageLayout.DOUBLE_PAGES -> true
                            PagerConfig.PageLayout.AUTOMATIC -> resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                            else -> false
                        },
                        true
                    )
                }
                .launchIn(lifecycleScope)
        }

        /**
         * Sets the visibility of the bottom page indicator according to [visible].
         */
        fun setPageNumberVisibility(visible: Boolean) {
            binding.pageNumber.isVisible = visible
        }

        /**
         * Sets the 32-bit color mode according to [enabled].
         */
        private fun setTrueColor(enabled: Boolean) {
            if (enabled) {
                SubsamplingScaleImageView.setPreferredBitmapConfig(Bitmap.Config.ARGB_8888)
            } else {
                SubsamplingScaleImageView.setPreferredBitmapConfig(Bitmap.Config.RGB_565)
            }
        }

        @TargetApi(Build.VERSION_CODES.P)
        private fun setCutoutShort(enabled: Boolean) {
            window.attributes.layoutInDisplayCutoutMode = when (enabled) {
                true -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                false -> WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
            }

            // Trigger relayout
            setMenuVisibility(menuVisible)
        }

        /**
         * Sets the keep screen on mode according to [enabled].
         */
        private fun setKeepScreenOn(enabled: Boolean) {
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        /**
         * Sets the custom brightness overlay according to [enabled].
         */
        private fun setCustomBrightness(enabled: Boolean) {
            if (enabled) {
                preferences.customBrightnessValue().asFlow()
                    .sample(100)
                    .onEach { setCustomBrightnessValue(it) }
                    .launchIn(lifecycleScope)
            } else {
                setCustomBrightnessValue(0)
            }
        }

        /**
         * Sets the color filter overlay according to [enabled].
         */
        private fun setColorFilter(enabled: Boolean) {
            if (enabled) {
                preferences.colorFilterValue().asFlow()
                    .sample(100)
                    .onEach { setColorFilterValue(it) }
                    .launchIn(lifecycleScope)
            } else {
                binding.colorOverlay.isVisible = false
            }
        }

        /**
         * Sets the brightness of the screen. Range is [-75, 100].
         * From -75 to -1 a semi-transparent black view is overlaid with the minimum brightness.
         * From 1 to 100 it sets that value as brightness.
         * 0 sets system brightness and hides the overlay.
         */
        private fun setCustomBrightnessValue(value: Int) {
            // Calculate and set reader brightness.
            val readerBrightness = when {
                value > 0 -> {
                    value / 100f
                }
                value < 0 -> {
                    0.01f
                }
                else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }

            window.attributes = window.attributes.apply { screenBrightness = readerBrightness }

            // Set black overlay visibility.
            if (value < 0) {
                binding.brightnessOverlay.isVisible = true
                val alpha = (abs(value) * 2.56).toInt()
                binding.brightnessOverlay.setBackgroundColor(Color.argb(alpha, 0, 0, 0))
            } else {
                binding.brightnessOverlay.isVisible = false
            }
        }

        /**
         * Sets the color filter [value].
         */
        private fun setColorFilterValue(value: Int) {
            binding.colorOverlay.isVisible = true
            binding.colorOverlay.setFilterColor(value, preferences.colorFilterMode().get())
        }

        private fun setGrayscale(enabled: Boolean) {
            val paint = if (enabled) grayscalePaint else null
            binding.viewerContainer.setLayerType(LAYER_TYPE_HARDWARE, paint)
        }
    }
}
