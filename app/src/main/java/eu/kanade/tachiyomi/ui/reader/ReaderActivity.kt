package eu.kanade.tachiyomi.ui.reader

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.ProgressDialog
import android.app.assist.AssistContent
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MotionEvent
import android.view.View.LAYER_TYPE_HARDWARE
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.transition.doOnEnd
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.slider.Slider
import com.google.android.material.transition.platform.MaterialContainerTransform
import dev.chrisbanes.insetter.applyInsetter
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.manga.model.orientationType
import eu.kanade.domain.manga.model.readingModeType
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.databinding.ReaderActivityBinding
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.AddToLibraryFirst
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Error
import eu.kanade.tachiyomi.ui.reader.ReaderViewModel.SetAsCoverResult.Success
import eu.kanade.tachiyomi.ui.reader.chapter.ReaderChapterDialog
import eu.kanade.tachiyomi.ui.reader.loader.HttpPageLoader
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.model.ViewerChapters
import eu.kanade.tachiyomi.ui.reader.setting.OrientationType
import eu.kanade.tachiyomi.ui.reader.setting.ReaderBottomButton
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import eu.kanade.tachiyomi.ui.reader.setting.ReaderSettingsSheet
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.ui.reader.viewer.BaseViewer
import eu.kanade.tachiyomi.ui.reader.viewer.ReaderProgressIndicator
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerConfig
import eu.kanade.tachiyomi.ui.reader.viewer.pager.PagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.R2LPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.pager.VerticalPagerViewer
import eu.kanade.tachiyomi.ui.reader.viewer.webtoon.WebtoonViewer
import eu.kanade.tachiyomi.ui.webview.WebViewActivity
import eu.kanade.tachiyomi.util.preference.toggle
import eu.kanade.tachiyomi.util.system.applySystemAnimatorScale
import eu.kanade.tachiyomi.util.system.createReaderThemeContext
import eu.kanade.tachiyomi.util.system.getThemeColor
import eu.kanade.tachiyomi.util.system.hasDisplayCutout
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.system.isNightMode
import eu.kanade.tachiyomi.util.system.toShareIntent
import eu.kanade.tachiyomi.util.system.toast
import eu.kanade.tachiyomi.util.view.copy
import eu.kanade.tachiyomi.util.view.popupMenu
import eu.kanade.tachiyomi.util.view.setTooltip
import eu.kanade.tachiyomi.widget.listener.SimpleAnimationListener
import exh.log.xLogE
import exh.source.isEhBasedSource
import exh.util.defaultReaderType
import exh.util.floor
import exh.util.mangaType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import logcat.LogPriority
import tachiyomi.core.Constants
import tachiyomi.core.util.lang.launchIO
import tachiyomi.core.util.lang.launchNonCancellable
import tachiyomi.core.util.lang.withUIContext
import tachiyomi.core.util.system.logcat
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.injectLazy
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

class ReaderActivity : BaseActivity() {

    companion object {

        fun newIntent(context: Context, mangaId: Long?, chapterId: Long?/* SY --> */, page: Int? = null/* SY <-- */): Intent {
            return Intent(context, ReaderActivity::class.java).apply {
                putExtra("manga", mangaId)
                putExtra("chapter", chapterId)
                // SY -->
                putExtra("page", page)
                // SY <--
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        const val SHIFT_DOUBLE_PAGES = "shiftingDoublePages"
        const val SHIFTED_PAGE_INDEX = "shiftedPageIndex"
        const val SHIFTED_CHAP_INDEX = "shiftedChapterIndex"

        private const val ENABLED_BUTTON_IMAGE_ALPHA = 255
        private const val DISABLED_BUTTON_IMAGE_ALPHA = 64
    }

    private val readerPreferences: ReaderPreferences by injectLazy()
    private val preferences: BasePreferences by injectLazy()

    lateinit var binding: ReaderActivityBinding

    val viewModel by viewModels<ReaderViewModel>()
    private var assistUrl: String? = null

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

    private val windowInsetsController by lazy { WindowInsetsControllerCompat(window, binding.root) }

    private var loadingIndicator: ReaderProgressIndicator? = null

    var isScrollingThroughPages = false
        private set

    /**
     * Called when the activity is created. Initializes the presenter and configuration.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        registerSecureActivity(this)
        overridePendingTransition(R.anim.shared_axis_x_push_enter, R.anim.shared_axis_x_push_exit)

        super.onCreate(savedInstanceState)

        binding = ReaderActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (viewModel.needsInit()) {
            val manga = intent.extras!!.getLong("manga", -1)
            val chapter = intent.extras!!.getLong("chapter", -1)
            // SY -->
            val page = intent.extras!!.getInt("page", -1).takeUnless { it == -1 }
            // SY <--
            if (manga == -1L || chapter == -1L) {
                finish()
                return
            }
            NotificationReceiver.dismissNotification(this, manga.hashCode(), Notifications.ID_NEW_CHAPTERS)

            lifecycleScope.launchNonCancellable {
                val initResult = viewModel.init(manga, chapter/* SY --> */, page/* SY <-- */)
                if (!initResult.getOrDefault(false)) {
                    val exception = initResult.exceptionOrNull() ?: IllegalStateException("Unknown err")
                    withUIContext {
                        setInitialChapterError(exception)
                    }
                }
            }
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

        // Finish when incognito mode is disabled
        preferences.incognitoMode().changes()
            .drop(1)
            .onEach { if (!it) finish() }
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.isLoadingAdjacentChapter }
            .distinctUntilChanged()
            .onEach(::setProgressDialog)
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.manga }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setManga)
            .launchIn(lifecycleScope)

        viewModel.state
            .map { it.viewerChapters }
            .distinctUntilChanged()
            .filterNotNull()
            .onEach(::setChapters)
            .launchIn(lifecycleScope)

        viewModel.eventFlow
            .onEach { event ->
                when (event) {
                    ReaderViewModel.Event.ReloadViewerChapters -> {
                        viewModel.state.value.viewerChapters?.let(::setChapters)
                    }
                    is ReaderViewModel.Event.SetOrientation -> {
                        setOrientation(event.orientation)
                    }
                    is ReaderViewModel.Event.SavedImage -> {
                        onSaveImageResult(event.result)
                    }
                    is ReaderViewModel.Event.ShareImage -> {
                        onShareImageResult(event.uri, event.page /* SY --> */, event.secondPage /* SY <-- */)
                    }
                    is ReaderViewModel.Event.SetCoverResult -> {
                        onSetAsCoverResult(event.result)
                    }
                }
            }
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
        viewModel.onSaveInstanceState()
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        viewModel.saveCurrentChapterReadingProgress()
        super.onPause()
    }

    /**
     * Set menu visibility again on activity resume to apply immersive mode again if needed.
     * Helps with rotations.
     */
    override fun onResume() {
        super.onResume()
        viewModel.setReadStartTime()
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

    override fun onProvideAssistContent(outContent: AssistContent) {
        super.onProvideAssistContent(outContent)
        assistUrl?.let { outContent.webUri = it.toUri() }
    }

    /**
     * Called when the options menu of the toolbar is being created. It adds our custom menu.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.reader, menu)

        /*val isChapterBookmarked = viewModel.getCurrentChapter()?.chapter?.bookmark ?: false
        menu.findItem(R.id.action_bookmark).isVisible = !isChapterBookmarked
        menu.findItem(R.id.action_remove_bookmark).isVisible = isChapterBookmarked
        menu.findItem(R.id.action_open_in_web_view).isVisible = viewModel.getSource() is HttpSource*/

        return true
    }

    /**
     * Called when an item of the options menu was clicked. Used to handle clicks on our menu
     * entries.
     */
    /*override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_open_in_web_view -> {
                openChapterInWebview()
            }
            R.id.action_bookmark -> {
                viewModel.bookmarkCurrentChapter(true)
                invalidateOptionsMenu()
            }
            R.id.action_remove_bookmark -> {
                viewModel.bookmarkCurrentChapter(false)
                invalidateOptionsMenu()
            }
        }
        return super.onOptionsItemSelected(item)
    }*/

    /**
     * Called when the user clicks the back key or the button on the toolbar. The call is
     * delegated to the presenter.
     */
    override fun finish() {
        viewModel.onActivityFinish()
        super.finish()
        overridePendingTransition(R.anim.shared_axis_x_pop_enter, R.anim.shared_axis_x_pop_exit)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_N) {
            loadNextChapter()
            return true
        } else if (keyCode == KeyEvent.KEYCODE_P) {
            loadPreviousChapter()
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

    // SY -->
    fun TextView.textChanges(): Flow<CharSequence> = callbackFlow {
        val listener = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                trySend(s)
            }

            override fun afterTextChanged(s: Editable) = Unit
        }

        addTextChangedListener(listener)
        awaitClose { removeTextChangedListener(listener) }
    }
        .conflate()
        .onStart { emit(text) }

    fun CompoundButton.checkedChanges(): Flow<Boolean> = callbackFlow {
        val listener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            trySend(isChecked)
        }
        setOnCheckedChangeListener(listener)

        awaitClose { setOnCheckedChangeListener(null) }
    }
        .conflate()
        .onStart { emit(isChecked) }
    // SY <--

    /**
     * Initializes the reader menu. It sets up click listeners and the initial visibility.
     */
    private fun initializeMenu() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        binding.header.applyInsetter {
            type(navigationBars = true, statusBars = true) {
                margin(top = true, horizontal = true)
            }
        }
        binding.readerMenuBottom.applyInsetter {
            type(navigationBars = true) {
                margin(bottom = true, horizontal = true)
            }
        }

        binding.toolbar.setOnClickListener {
            viewModel.manga?.id?.let { id ->
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        action = Constants.SHORTCUT_MANGA
                        putExtra(Constants.MANGA_EXTRA, id)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    },
                )
            }
        }

        // SY -->
        // Init listeners on bottom menu
        val listener = object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                isScrollingThroughPages = true
            }

            override fun onStopTrackingTouch(slider: Slider) {
                isScrollingThroughPages = false
            }
        }
        val onChangeListener = Slider.OnChangeListener { slider, value, fromUser ->
            if (viewer != null && fromUser) {
                isScrollingThroughPages = true
                moveToPageIndex(value.toInt())
                slider.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
        }
        listOf(binding.pageSlider, binding.pageSliderVert)
            .forEach {
                it.addOnSliderTouchListener(listener)
                it.addOnChangeListener(onChangeListener)
            }
        // SY <--

        // Extra menu buttons

        // SY -->
        listOf(binding.leftChapter, binding.aboveChapter).forEach {
            it.setOnClickListener {
                if (viewer != null) {
                    if (viewer is R2LPagerViewer) {
                        loadNextChapter()
                    } else {
                        loadPreviousChapter()
                    }
                }
            }
        }
        listOf(binding.rightChapter, binding.belowChapter).forEach {
            it.setOnClickListener {
                if (viewer != null) {
                    if (viewer is R2LPagerViewer) {
                        loadPreviousChapter()
                    } else {
                        loadNextChapter()
                    }
                }
            }
        }

        initBottomShortcuts()

        // SY <--
        updateBottomButtons()

        initDropdownMenu()

        val toolbarBackground = (binding.toolbar.background as MaterialShapeDrawable).apply {
            elevation = resources.getDimension(R.dimen.m3_sys_elevation_level2)
            alpha = if (isNightMode()) 230 else 242 // 90% dark 95% light
        }
        binding.toolbarBottom.background = toolbarBackground.copy(this@ReaderActivity)

        binding.readerSeekbar.background = toolbarBackground.copy(this@ReaderActivity)?.apply {
            setCornerSize(999F)
        }
        // SY -->
        binding.readerSeekbarVert.background = toolbarBackground.copy(this@ReaderActivity)?.apply {
            setCornerSize(999F)
        }
        // SY <--
        listOf(binding.leftChapter, binding.rightChapter /* SY --> */, binding.belowChapter, binding.aboveChapter /* SY <-- */).forEach {
            it.background = binding.readerSeekbar.background.copy(this)
            it.foreground = RippleDrawable(
                ColorStateList.valueOf(getThemeColor(android.R.attr.colorControlHighlight)),
                null,
                it.background,
            )
        }

        val toolbarColor = ColorUtils.setAlphaComponent(
            toolbarBackground.resolvedTintColor,
            toolbarBackground.alpha,
        )
        window.statusBarColor = toolbarColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            window.navigationBarColor = toolbarColor
        }
        // SY -->
        binding.toolbar.background.alpha = 0
        (binding.header.background as MaterialShapeDrawable).fillColor = ColorStateList.valueOf(toolbarColor)
        // SY <--

        // Set initial visibility
        setMenuVisibility(menuVisible)

        // --> EH
        setEhUtilsVisibility(ehUtilsVisible)
        // <-- EH
    }

    // EXH -->
    fun initBottomShortcuts() {
        // Reading mode
        with(binding.actionReadingMode) {
            setTooltip(R.string.viewer)

            setOnClickListener {
                popupMenu(
                    items = ReadingModeType.values().map { it.flagValue to it.stringRes },
                    selectedItemId = viewModel.getMangaReadingMode(resolveDefault = false),
                ) {
                    val newReadingMode = ReadingModeType.fromPreference(itemId)

                    viewModel.setMangaReadingMode(newReadingMode.flagValue)

                    menuToggleToast?.cancel()
                    if (!readerPreferences.showReadingMode().get()) {
                        menuToggleToast = toast(newReadingMode.stringRes)
                    }

                    updateCropBordersShortcut()
                }
            }
        }

        // Crop borders
        with(binding.actionCropBorders) {
            setTooltip(R.string.pref_crop_borders)

            setOnClickListener {
                // SY -->
                val mangaViewer = viewModel.getMangaReadingMode()
                // SY <--
                val isPagerType = ReadingModeType.isPagerType(mangaViewer)
                val enabled = if (isPagerType) {
                    readerPreferences.cropBorders().toggle()
                } else {
                    // SY -->
                    if (ReadingModeType.fromPreference(mangaViewer) == ReadingModeType.CONTINUOUS_VERTICAL) {
                        readerPreferences.cropBordersContinuousVertical().toggle()
                    } else {
                        readerPreferences.cropBordersWebtoon().toggle()
                    }
                    // SY <--
                }

                menuToggleToast?.cancel()
                menuToggleToast = toast(
                    if (enabled) {
                        R.string.on
                    } else {
                        R.string.off
                    },
                )
            }
        }
        updateCropBordersShortcut()
        listOf(readerPreferences.cropBorders(), readerPreferences.cropBordersWebtoon() /* SY --> */, readerPreferences.cropBordersContinuousVertical()/* SY <-- */)
            .forEach { pref ->
                pref.changes()
                    .onEach { updateCropBordersShortcut() }
                    .launchIn(lifecycleScope)
            }

        // Rotation
        with(binding.actionRotation) {
            setTooltip(R.string.rotation_type)

            setOnClickListener {
                popupMenu(
                    items = OrientationType.values().map { it.flagValue to it.stringRes },
                    selectedItemId = viewModel.manga?.orientationType?.toInt()
                        ?: readerPreferences.defaultOrientationType().get(),
                ) {
                    val newOrientation = OrientationType.fromPreference(itemId)

                    viewModel.setMangaOrientationType(newOrientation.flagValue)

                    menuToggleToast?.cancel()
                    menuToggleToast = toast(newOrientation.stringRes)
                }
            }
        }

        // Settings sheet
        with(binding.actionSettings) {
            setTooltip(R.string.action_settings)

            var readerSettingSheet: ReaderSettingsSheet? = null

            setOnClickListener {
                if (readerSettingSheet?.isShowing == true) return@setOnClickListener
                readerSettingSheet = ReaderSettingsSheet(this@ReaderActivity).apply { show() }
            }

            setOnLongClickListener {
                if (readerSettingSheet?.isShowing == true) return@setOnLongClickListener false
                readerSettingSheet = ReaderSettingsSheet(this@ReaderActivity, showColorFilterSettings = true).apply { show() }
                true
            }
        }

        // --> EH
        with(binding.actionWebView) {
            setTooltip(R.string.action_open_in_web_view)

            setOnClickListener {
                openChapterInWebview()
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
                if (readerPreferences.pageLayout().get() == PagerConfig.PageLayout.AUTOMATIC) {
                    (viewer as? PagerViewer)?.config?.let { config ->
                        config.doublePages = !config.doublePages
                        reloadChapters(config.doublePages, true)
                    }
                    updateBottomButtons()
                } else {
                    readerPreferences.pageLayout().set(1 - readerPreferences.pageLayout().get())
                }
            }
        }
        with(binding.shiftPageButton) {
            setTooltip(R.string.shift_double_pages)

            setOnClickListener {
                shiftDoublePages()
            }
        }
    }

    fun initDropdownMenu() {
        binding.expandEhButton.setOnClickListener {
            ehUtilsVisible = !ehUtilsVisible
            setEhUtilsVisibility(ehUtilsVisible)
        }

        binding.ehAutoscrollFreq.setText(
            readerPreferences.autoscrollInterval().get().let {
                if (it == -1f) {
                    ""
                } else {
                    it.toString()
                }
            },
        )

        binding.ehAutoscroll.checkedChanges()
            .combine(binding.ehAutoscrollFreq.textChanges()) { checked, text ->
                checked to text
            }
            .mapLatest { (checked, text) ->
                val parsed = text.toString().toDoubleOrNull()

                if (parsed == null || parsed <= 0 || parsed > 9999) {
                    binding.ehAutoscrollFreq.error = getString(R.string.eh_autoscroll_freq_invalid)
                    readerPreferences.autoscrollInterval().set(-1f)
                    binding.ehAutoscroll.isEnabled = false
                } else {
                    binding.ehAutoscrollFreq.error = null
                    readerPreferences.autoscrollInterval().set(parsed.toFloat())
                    binding.ehAutoscroll.isEnabled = true
                    if (checked) {
                        repeatOnLifecycle(Lifecycle.State.STARTED) {
                            val interval = parsed.seconds
                            while (true) {
                                if (!binding.readerMenu.isVisible) {
                                    viewer.let { v ->
                                        when (v) {
                                            is PagerViewer -> v.moveToNext()
                                            is WebtoonViewer -> {
                                                if (readerPreferences.smoothAutoScroll().get()) {
                                                    v.linearScroll(interval)
                                                } else {
                                                    v.scrollDown()
                                                }
                                            }
                                        }
                                    }
                                    delay(interval)
                                } else {
                                    delay(100)
                                }
                            }
                        }
                    }
                }
            }
            .launchIn(lifecycleScope)

        binding.ehAutoscrollHelp.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.eh_autoscroll_help)
                .setMessage(R.string.eh_autoscroll_help_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        binding.ehRetryAll.setOnClickListener {
            var retried = 0

            viewModel.state.value.viewerChapters
                ?.currChapter
                ?.pages
                ?.forEachIndexed { _, page ->
                    var shouldQueuePage = false
                    if (page.status == Page.State.ERROR) {
                        shouldQueuePage = true
                    } /*else if (page.status == Page.LOAD_PAGE ||
                                page.status == Page.DOWNLOAD_IMAGE) {
                            // Do nothing
                        }*/

                    if (shouldQueuePage) {
                        page.status = Page.State.QUEUE
                    } else {
                        return@forEachIndexed
                    }

                    // If we are using EHentai/ExHentai, get a new image URL
                    viewModel.manga?.let { m ->
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

            toast(resources.getQuantityString(R.plurals.eh_retry_toast, retried, retried))
        }

        binding.ehRetryAllHelp.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.eh_retry_all_help)
                .setMessage(R.string.eh_retry_all_help_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        binding.ehBoostPage.setOnClickListener {
            viewer ?: return@setOnClickListener
            val curPage = exhCurrentpage() ?: run {
                toast(R.string.eh_boost_page_invalid)
                return@setOnClickListener
            }

            if (curPage.status == Page.State.ERROR) {
                toast(R.string.eh_boost_page_errored)
            } else if (curPage.status == Page.State.LOAD_PAGE || curPage.status == Page.State.DOWNLOAD_IMAGE) {
                toast(R.string.eh_boost_page_downloading)
            } else if (curPage.status == Page.State.READY) {
                toast(R.string.eh_boost_page_downloaded)
            } else {
                val loader = (viewModel.state.value.viewerChapters?.currChapter?.pageLoader as? HttpPageLoader)
                if (loader != null) {
                    loader.boostPage(curPage)
                    toast(R.string.eh_boost_boosted)
                } else {
                    toast(R.string.eh_boost_invalid_loader)
                }
            }
        }

        binding.ehBoostPageHelp.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.eh_boost_page_help)
                .setMessage(R.string.eh_boost_page_help_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }
    }

    private fun exhCurrentpage(): ReaderPage? {
        val currentPage = (((viewer as? PagerViewer)?.currentPage ?: (viewer as? WebtoonViewer)?.currentPage) as? ReaderPage)?.index
        return currentPage?.let { viewModel.state.value.viewerChapters?.currChapter?.pages?.getOrNull(it) }
    }

    fun updateBottomButtons() {
        val enabledButtons = readerPreferences.readerBottomButtons().get()
        with(binding) {
            actionReadingMode.isVisible = ReaderBottomButton.ReadingMode.isIn(enabledButtons)
            actionRotation.isVisible =
                ReaderBottomButton.Rotation.isIn(enabledButtons)
            doublePage.isVisible =
                viewer is PagerViewer && ReaderBottomButton.PageLayout.isIn(enabledButtons) && !readerPreferences.dualPageSplitPaged().get()
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
                ReaderBottomButton.WebView.isIn(enabledButtons) && viewModel.getSource() is HttpSource
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
        val currentChapter = viewModel.getCurrentChapter()
        if (doublePages) {
            // If we're moving from singe to double, we want the current page to be the first page
            pViewer.config.shiftDoublePage = (
                binding.pageSlider.value.floor() +
                    (currentChapter?.pages?.take(binding.pageSlider.value.floor())?.count { it.fullPage || it.isolatedPage } ?: 0)
                ) % 2 != 0
        }
        viewModel.state.value.viewerChapters?.let {
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
            viewModel.state.value.viewerChapters?.let {
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
        val mangaViewer = viewModel.getMangaReadingMode()
        val isPagerType = ReadingModeType.isPagerType(mangaViewer)
        val enabled = if (isPagerType) {
            readerPreferences.cropBorders().get()
        } else {
            // SY -->
            if (ReadingModeType.fromPreference(mangaViewer) == ReadingModeType.CONTINUOUS_VERTICAL) {
                readerPreferences.cropBordersContinuousVertical().get()
            } else {
                readerPreferences.cropBordersWebtoon().get()
            }
            // SY <--
        }

        binding.actionCropBorders.setImageResource(
            if (enabled) {
                R.drawable.ic_crop_24dp
            } else {
                R.drawable.ic_crop_off_24dp
            },
        )
    }

    /**
     * Sets the visibility of the menu according to [visible] and with an optional parameter to
     * [animate] the views.
     */
    fun setMenuVisibility(visible: Boolean, animate: Boolean = true) {
        menuVisible = visible
        if (visible) {
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            binding.readerMenu.isVisible = true

            if (animate) {
                val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.enter_from_top)
                toolbarAnimation.applySystemAnimatorScale(this)
                toolbarAnimation.setAnimationListener(
                    object : SimpleAnimationListener() {
                        override fun onAnimationStart(animation: Animation) {
                            // Fix status bar being translucent the first time it's opened.
                            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                        }
                    },
                )
                // EXH -->
                binding.header.startAnimation(toolbarAnimation)
                // EXH <--

                val vertAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_in_side)
                val vertAnimationLeft = AnimationUtils.loadAnimation(this, R.anim.fade_in_side_left)
                if (readerPreferences.leftVerticalSeekbar().get() && binding.readerNavVert.isVisible) {
                    binding.seekbarVertContainer.startAnimation(vertAnimationLeft)
                } else {
                    binding.seekbarVertContainer.startAnimation(vertAnimation)
                }

                val bottomAnimation = AnimationUtils.loadAnimation(this, R.anim.enter_from_bottom)
                bottomAnimation.applySystemAnimatorScale(this)
                binding.readerMenuBottom.startAnimation(bottomAnimation)
            }

            if (readerPreferences.showPageNumber().get()) {
                config?.setPageNumberVisibility(false)
            }
        } else {
            if (readerPreferences.fullscreen().get()) {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }

            if (animate) {
                val toolbarAnimation = AnimationUtils.loadAnimation(this, R.anim.exit_to_top)
                toolbarAnimation.applySystemAnimatorScale(this)
                toolbarAnimation.setAnimationListener(
                    object : SimpleAnimationListener() {
                        override fun onAnimationEnd(animation: Animation) {
                            binding.readerMenu.isVisible = false
                        }
                    },
                )
                // EXH -->
                binding.header.startAnimation(toolbarAnimation)
                // EXH <--

                val vertAnimation = AnimationUtils.loadAnimation(this, R.anim.fade_out_side)
                val vertAnimationLeft = AnimationUtils.loadAnimation(this, R.anim.fade_out_side_left)
                if (readerPreferences.leftVerticalSeekbar().get() && binding.readerNavVert.isVisible) {
                    binding.seekbarVertContainer.startAnimation(vertAnimationLeft)
                } else {
                    binding.seekbarVertContainer.startAnimation(vertAnimation)
                }

                val bottomAnimation = AnimationUtils.loadAnimation(this, R.anim.exit_to_bottom)
                bottomAnimation.applySystemAnimatorScale(this)
                binding.readerMenuBottom.startAnimation(bottomAnimation)
            }

            if (readerPreferences.showPageNumber().get()) {
                config?.setPageNumberVisibility(true)
            }
        }
    }

    /**
     * Called from the presenter when a manga is ready. Used to instantiate the appropriate viewer
     * and the toolbar title.
     */
    private fun setManga(manga: Manga) {
        val prevViewer = viewer

        val viewerMode = ReadingModeType.fromPreference(viewModel.getMangaReadingMode(resolveDefault = false))
        binding.actionReadingMode.setImageResource(viewerMode.iconRes)

        val newViewer = ReadingModeType.toViewer(viewModel.getMangaReadingMode(), this)

        updateCropBordersShortcut()
        if (window.sharedElementEnterTransition is MaterialContainerTransform) {
            // Wait until transition is complete to avoid crash on API 26
            window.sharedElementEnterTransition.doOnEnd {
                setOrientation(viewModel.getMangaOrientationType())
            }
        } else {
            setOrientation(viewModel.getMangaOrientationType())
        }

        // Destroy previous viewer if there was one
        if (prevViewer != null) {
            prevViewer.destroy()
            binding.viewerContainer.removeAllViews()
        }
        viewer = newViewer
        updateViewerInset(readerPreferences.fullscreen().get())
        binding.viewerContainer.addView(newViewer.getView())

        // SY -->
        if (newViewer is PagerViewer) {
            if (readerPreferences.pageLayout().get() == PagerConfig.PageLayout.AUTOMATIC) {
                setDoublePageMode(newViewer)
            }
            lastShiftDoubleState?.let { newViewer.config.shiftDoublePage = it }
        }

        val defaultReaderType = manga.defaultReaderType(manga.mangaType(sourceName = sourceManager.get(manga.source)?.name))
        if (readerPreferences.useAutoWebtoon().get() && manga.readingModeType.toInt() == ReadingModeType.DEFAULT.flagValue && defaultReaderType != null && defaultReaderType == ReadingModeType.WEBTOON.prefValue) {
            readingModeToast?.cancel()
            readingModeToast = toast(resources.getString(R.string.eh_auto_webtoon_snack))
        } else if (readerPreferences.showReadingMode().get()) {
            // SY <--
            showReadingModeToast(viewModel.getMangaReadingMode())
        }

        // SY -->

        // --> Vertical seekbar hide on landscape

        if (
            !readerPreferences.forceHorizontalSeekbar().get() &&
            (
                (
                    resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && readerPreferences.landscapeVerticalSeekbar().get()
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
        if (readerPreferences.leftVerticalSeekbar().get() && binding.readerNavVert.isVisible) {
            params.removeRule(RelativeLayout.ALIGN_PARENT_END)
            binding.readerNavVert.layoutParams = params
        }

        // <-- Left-handed vertical seekbar

        updateBottomButtons()
        // SY <--
        supportActionBar?.title = manga.title

        binding.pageSlider.isRTL = newViewer is R2LPagerViewer
        if (newViewer is R2LPagerViewer) {
            binding.leftChapter.setTooltip(R.string.action_next_chapter)
            binding.rightChapter.setTooltip(R.string.action_previous_chapter)
        } else {
            binding.leftChapter.setTooltip(R.string.action_previous_chapter)
            binding.rightChapter.setTooltip(R.string.action_next_chapter)
        }
        binding.aboveChapter.setTooltip(R.string.action_previous_chapter)
        binding.belowChapter.setTooltip(R.string.action_next_chapter)

        val loadingIndicatorContext = createReaderThemeContext()
        loadingIndicator = ReaderProgressIndicator(loadingIndicatorContext).apply {
            updateLayoutParams<FrameLayout.LayoutParams> {
                gravity = Gravity.CENTER
            }
        }
        binding.readerContainer.addView(loadingIndicator)

        startPostponedEnterTransition()
    }

    private fun openChapterInWebview() {
        val manga = viewModel.manga ?: return
        val source = viewModel.getSource() ?: return
        lifecycleScope.launchIO {
            viewModel.getChapterUrl()?.let { url ->
                val intent = WebViewActivity.newIntent(this@ReaderActivity, url, source.id, manga.title)
                withUIContext { startActivity(intent) }
            }
        }
    }

    private fun showReadingModeToast(mode: Int) {
        try {
            val strings = resources.getStringArray(R.array.viewers_selector)
            readingModeToast?.cancel()
            readingModeToast = toast(strings[mode])
        } catch (e: ArrayIndexOutOfBoundsException) {
            logcat(LogPriority.ERROR) { "Unknown reading mode: $mode" }
        }
    }

    /**
     * Called from the presenter whenever a new [viewerChapters] have been set. It delegates the
     * method to the current viewer, but also set the subtitle on the toolbar, and
     * hides or disables the reader prev/next buttons if there's a prev or next chapter
     */
    private fun setChapters(viewerChapters: ViewerChapters) {
        binding.readerContainer.removeView(loadingIndicator)
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

        val currentChapterPageCount = viewerChapters.currChapter.pages?.size ?: 1
        binding.readerSeekbar.isInvisible = currentChapterPageCount == 1
        binding.readerSeekbarVert.isInvisible = currentChapterPageCount == 1

        val leftChapterObject = if (viewer is R2LPagerViewer) viewerChapters.nextChapter else viewerChapters.prevChapter
        val rightChapterObject = if (viewer is R2LPagerViewer) viewerChapters.prevChapter else viewerChapters.nextChapter

        if (leftChapterObject == null && rightChapterObject == null) {
            binding.leftChapter.isVisible = false
            binding.rightChapter.isVisible = false
            binding.aboveChapter.isVisible = false
            binding.belowChapter.isVisible = false
        } else {
            binding.leftChapter.isEnabled = leftChapterObject != null
            binding.leftChapter.imageAlpha = if (leftChapterObject != null) ENABLED_BUTTON_IMAGE_ALPHA else DISABLED_BUTTON_IMAGE_ALPHA

            binding.rightChapter.isEnabled = rightChapterObject != null
            binding.rightChapter.imageAlpha = if (rightChapterObject != null) ENABLED_BUTTON_IMAGE_ALPHA else DISABLED_BUTTON_IMAGE_ALPHA

            binding.aboveChapter.isEnabled = leftChapterObject != null
            binding.aboveChapter.imageAlpha = if (leftChapterObject != null) ENABLED_BUTTON_IMAGE_ALPHA else DISABLED_BUTTON_IMAGE_ALPHA

            binding.belowChapter.isEnabled = rightChapterObject != null
            binding.belowChapter.imageAlpha = if (rightChapterObject != null) ENABLED_BUTTON_IMAGE_ALPHA else DISABLED_BUTTON_IMAGE_ALPHA
        }

        // Invalidate menu to show proper chapter bookmark state
        invalidateOptionsMenu()

        lifecycleScope.launchIO {
            viewModel.getChapterUrl()?.let { url ->
                assistUrl = url
            }
        }
    }

    /**
     * Called from the presenter if the initial load couldn't load the pages of the chapter. In
     * this case the activity is closed and a toast is shown to the user.
     */
    private fun setInitialChapterError(error: Throwable) {
        logcat(LogPriority.ERROR, error)
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
    private fun moveToPageIndex(index: Int) {
        val viewer = viewer ?: return
        val currentChapter = viewModel.getCurrentChapter() ?: return
        val page = currentChapter.pages?.getOrNull(index) ?: return
        viewer.moveToPage(page)
    }

    /**
     * Tells the presenter to load the next chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    private fun loadNextChapter() {
        lifecycleScope.launch {
            viewModel.loadNextChapter()
            moveToPageIndex(0)
        }
    }

    /**
     * Tells the presenter to load the previous chapter and mark it as active. The progress dialog
     * should be automatically shown.
     */
    private fun loadPreviousChapter() {
        lifecycleScope.launch {
            viewModel.loadPreviousChapter()
            moveToPageIndex(0)
        }
    }

    /**
     * Called from the viewer whenever a [page] is marked as active. It updates the values of the
     * bottom menu and delegates the change to the presenter.
     */
    @SuppressLint("SetTextI18n")
    fun onPageSelected(page: ReaderPage, hasExtraPage: Boolean = false) {
        val newChapter = viewModel.onPageSelected(page, hasExtraPage)
        val pages = page.chapter.pages ?: return

        val currentPage = if (hasExtraPage) {
            val invertDoublePage = (viewer as? PagerViewer)?.config?.invertDoublePages ?: false
            if (resources.isLTR xor invertDoublePage) "${page.number}-${page.number + 1}" else "${page.number + 1}-${page.number}"
        } else {
            "${page.number}"
        }

        // Set bottom page number
        binding.pageNumber.text = "$currentPage/${pages.size}"
        // binding.pageText.text = "${page.number}/${pages.size}"

        // Set page numbers
        if (viewer !is R2LPagerViewer) {
            binding.leftPageText.text = currentPage
            binding.rightPageText.text = "${pages.size}"
        } else {
            binding.rightPageText.text = currentPage
            binding.leftPageText.text = "${pages.size}"
        }

        // Set slider progress
        binding.pageSlider.isEnabled = pages.size > 1
        binding.pageSlider.valueTo = max(pages.lastIndex.toFloat(), 1f)
        binding.pageSlider.value = page.index.toFloat()

        // SY -->
        binding.pageSliderVert.valueTo = max(pages.lastIndex.toFloat(), 1f)
        binding.pageSliderVert.value = page.index.toFloat()
        // SY <--

        // SY -->
        binding.abovePageText.text = currentPage
        binding.belowPageText.text = "${pages.size}"
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
                viewer?.config?.pageCanvasColor,
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
        lifecycleScope.launchIO { viewModel.preloadChapter(chapter) }
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
     * Called from the viewer to hide the menu.
     */
    fun hideMenu() {
        if (menuVisible) {
            setMenuVisibility(false)
        }
    }

    /**
     * Called from the page sheet. It delegates the call to the presenter to do some IO, which
     * will call [onShareImageResult] with the path the image was saved on when it's ready.
     */
    fun shareImage(page: ReaderPage) {
        viewModel.shareImage(page)
    }

    // SY -->
    fun shareImages(firstPage: ReaderPage, secondPage: ReaderPage, isLTR: Boolean, @ColorInt bg: Int) {
        viewModel.shareImages(firstPage, secondPage, isLTR, bg)
    }
    // SY <--

    /**
     * Called from the presenter when a page is ready to be shared. It shows Android's default
     * sharing tool.
     */
    fun onShareImageResult(uri: Uri, page: ReaderPage /* SY --> */, secondPage: ReaderPage? = null /* SY <-- */) {
        val manga = viewModel.manga ?: return
        val chapter = page.chapter.chapter

        // SY -->
        val text = if (secondPage != null) {
            getString(R.string.share_pages_info, manga.title, chapter.name, if (resources.isLTR) "${page.number}-${page.number + 1}" else "${page.number + 1}-${page.number}")
        } else {
            getString(R.string.share_page_info, manga.title, chapter.name, page.number)
        }
        // SY <--

        val intent = uri.toShareIntent(
            context = applicationContext,
            message = /* SY --> */ text, // SY <--
        )
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
    }

    /**
     * Called from the page sheet. It delegates saving the image of the given [page] on external
     * storage to the presenter.
     */
    fun saveImage(page: ReaderPage) {
        viewModel.saveImage(page)
    }

    // SY -->
    fun saveImages(firstPage: ReaderPage, secondPage: ReaderPage, isLTR: Boolean, @ColorInt bg: Int) {
        viewModel.saveImages(firstPage, secondPage, isLTR, bg)
    }
    // SY <--

    /**
     * Called from the presenter when a page is saved or fails. It shows a message or logs the
     * event depending on the [result].
     */
    private fun onSaveImageResult(result: ReaderViewModel.SaveImageResult) {
        when (result) {
            is ReaderViewModel.SaveImageResult.Success -> {
                toast(R.string.picture_saved)
            }
            is ReaderViewModel.SaveImageResult.Error -> {
                logcat(LogPriority.ERROR, result.error)
            }
        }
    }

    /**
     * Called from the page sheet. It delegates setting the image of the given [page] as the
     * cover to the presenter.
     */
    fun setAsCover(page: ReaderPage) {
        viewModel.setAsCover(page)
    }

    /**
     * Called from the presenter when a page is set as cover or fails. It shows a different message
     * depending on the [result].
     */
    private fun onSetAsCoverResult(result: ReaderViewModel.SetAsCoverResult) {
        toast(
            when (result) {
                Success -> R.string.cover_updated
                AddToLibraryFirst -> R.string.notification_first_add_to_library
                Error -> R.string.notification_cover_update_failed
            },
        )
    }

    /**
     * Forces the user preferred [orientation] on the activity.
     */
    private fun setOrientation(orientation: Int) {
        val newOrientation = OrientationType.fromPreference(orientation)
        if (newOrientation.flag != requestedOrientation) {
            requestedOrientation = newOrientation.flag
        }
        updateOrientationShortcut(viewModel.getMangaOrientationType(resolveDefault = false))
    }

    /**
     * Updates viewer inset depending on fullscreen reader preferences.
     */
    fun updateViewerInset(fullscreen: Boolean) {
        viewer?.getView()?.applyInsetter {
            if (!fullscreen) {
                type(navigationBars = true, statusBars = true) {
                    padding()
                }
            }
        }
    }

    /**
     * Class that handles the user preferences of the reader.
     */
    private inner class ReaderConfig {

        private fun getCombinedPaint(grayscale: Boolean, invertedColors: Boolean): Paint {
            return Paint().apply {
                colorFilter = ColorMatrixColorFilter(
                    ColorMatrix().apply {
                        if (grayscale) {
                            setSaturation(0f)
                        }
                        if (invertedColors) {
                            postConcat(
                                ColorMatrix(
                                    floatArrayOf(
                                        -1f, 0f, 0f, 0f, 255f,
                                        0f, -1f, 0f, 0f, 255f,
                                        0f, 0f, -1f, 0f, 255f,
                                        0f, 0f, 0f, 1f, 0f,
                                    ),
                                ),
                            )
                        }
                    },
                )
            }
        }

        /**
         * Initializes the reader subscriptions.
         */
        init {
            readerPreferences.readerTheme().changes()
                .onEach { theme ->
                    binding.readerContainer.setBackgroundResource(
                        when (theme) {
                            0 -> R.color.md_white_1000
                            2 -> R.color.reader_background_dark
                            3 -> automaticBackgroundColor()
                            else -> R.color.md_black_1000
                        },
                    )
                }
                .launchIn(lifecycleScope)

            readerPreferences.showPageNumber().changes()
                .onEach(::setPageNumberVisibility)
                .launchIn(lifecycleScope)

            readerPreferences.trueColor().changes()
                .onEach(::setTrueColor)
                .launchIn(lifecycleScope)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                readerPreferences.cutoutShort().changes()
                    .onEach(::setCutoutShort)
                    .launchIn(lifecycleScope)
            }

            readerPreferences.keepScreenOn().changes()
                .onEach(::setKeepScreenOn)
                .launchIn(lifecycleScope)

            readerPreferences.customBrightness().changes()
                .onEach(::setCustomBrightness)
                .launchIn(lifecycleScope)

            readerPreferences.colorFilter().changes()
                .onEach(::setColorFilter)
                .launchIn(lifecycleScope)

            readerPreferences.colorFilterMode().changes()
                .onEach { setColorFilter(readerPreferences.colorFilter().get()) }
                .launchIn(lifecycleScope)

            merge(readerPreferences.grayscale().changes(), readerPreferences.invertedColors().changes())
                .onEach { setLayerPaint(readerPreferences.grayscale().get(), readerPreferences.invertedColors().get()) }
                .launchIn(lifecycleScope)

            readerPreferences.fullscreen().changes()
                .onEach {
                    WindowCompat.setDecorFitsSystemWindows(window, !it)
                    updateViewerInset(it)
                }
                .launchIn(lifecycleScope)

            // SY -->
            readerPreferences.pageLayout().changes()
                .drop(1)
                .onEach { updateBottomButtons() }
                .launchIn(lifecycleScope)

            readerPreferences.dualPageSplitPaged().changes()
                .drop(1)
                .onEach {
                    if (viewer !is PagerViewer) return@onEach
                    updateBottomButtons()
                    reloadChapters(
                        !it && when (readerPreferences.pageLayout().get()) {
                            PagerConfig.PageLayout.DOUBLE_PAGES -> true
                            PagerConfig.PageLayout.AUTOMATIC -> resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                            else -> false
                        },
                        true,
                    )
                }
                .launchIn(lifecycleScope)
            // SY <--
        }

        /**
         * Picks background color for [ReaderActivity] based on light/dark theme preference
         */
        private fun automaticBackgroundColor(): Int {
            return if (baseContext.isNightMode()) {
                R.color.reader_background_dark
            } else {
                R.color.md_white_1000
            }
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
                readerPreferences.customBrightnessValue().changes()
                    .sample(100)
                    .onEach(::setCustomBrightnessValue)
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
                readerPreferences.colorFilterValue().changes()
                    .sample(100)
                    .onEach(::setColorFilterValue)
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
            binding.colorOverlay.setFilterColor(value, readerPreferences.colorFilterMode().get())
        }

        private fun setLayerPaint(grayscale: Boolean, invertedColors: Boolean) {
            val paint = if (grayscale || invertedColors) getCombinedPaint(grayscale, invertedColors) else null
            binding.viewerContainer.setLayerType(LAYER_TYPE_HARDWARE, paint)
        }
    }
}
