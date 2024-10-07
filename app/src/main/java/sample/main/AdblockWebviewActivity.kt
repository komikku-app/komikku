package sample.main

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.webkit.URLUtil
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.PopupMenu
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.ActivityMainBinding
import io.github.edsuns.adfilter.AdFilter
import io.github.edsuns.adfilter.FilterResult
import io.github.edsuns.adfilter.FilterViewModel
import io.github.edsuns.smoothprogress.SmoothProgressAnimator
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sample.hideKeyboard
import sample.main.blocking.BlockingInfoDialogFragment
import sample.settings.AdblockSettingsActivity
import sample.smartUrlFilter
import java.util.Locale

class AdblockWebviewActivity :
    AppCompatActivity(),
    WebViewClientListener {
    private lateinit var filterViewModel: FilterViewModel

    private val viewModel: MainViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView
    private lateinit var progressAnimator: SmoothProgressAnimator

    private lateinit var blockingInfoDialogFragment: BlockingInfoDialogFragment

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val filter = AdFilter.get()
        filterViewModel = filter.viewModel

        val popupMenu = PopupMenu(
            this,
            binding.menuButton,
            Gravity.NO_GRAVITY,
            androidx.appcompat.R.attr.actionOverflowMenuStyle,
            0,
        )
        popupMenu.inflate(R.menu.menu_main)
        popupMenu.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menuRefresh -> webView.reload()
                R.id.menuForward -> webView.goForward()
                R.id.menuSettings ->
                    startActivity(Intent(this, AdblockSettingsActivity::class.java))
                else -> finish()
            }
            true
        }
        val menuForward = popupMenu.menu.findItem(R.id.menuForward)

        binding.menuButton.setOnClickListener {
            menuForward.isVisible = webView.canGoForward()
            popupMenu.show()
        }

        blockingInfoDialogFragment = BlockingInfoDialogFragment.newInstance()

        binding.countText.setOnClickListener {
            if (filterViewModel.isFilterOn()) {
                // fix `IllegalStateException: Fragment already added` when double click
                if (!blockingInfoDialogFragment.isAdded) {
                    blockingInfoDialogFragment.show(supportFragmentManager, null)
                }
            } else {
                startActivity(Intent(this, AdblockSettingsActivity::class.java))
            }
        }

        webView = binding.webView
        webView.webViewClient = WebClient(this)
        webView.webChromeClient = ChromeClient(this)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.databaseEnabled = true
        settings.domStorageEnabled = true
        // Zooms out the content to fit on screen by width. For example, showing images.
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        // enable touch zoom controls
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        // allow Mixed Content
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        filter.setupWebView(webView)

        progressAnimator = SmoothProgressAnimator(binding.loadProgress)

        val urlText = binding.urlEditText
        webView.setOnTouchListener { v, _ ->
            if (urlText.isFocused) {
                urlText.hideKeyboard()
                v.requestFocus()
            }
            false
        }
        urlText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            ) {
                val urlIn = urlText.text.toString()
                // If input text is not an URL then perform web-search
                webView.loadUrl(
                    urlIn.smartUrlFilter() ?: URLUtil.composeSearchUrl(
                        urlIn,
                        "https://www.bing.com/search?q={}",
                        "{}",
                    ),
                )
                webView.requestFocus()
                urlText.hideKeyboard()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
        urlText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                urlText.setText(viewModel.currentPageUrl.value)
            }
        }

        // Update url textbox when page might redirect itself to new url
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentPageUrl.collect {
                    if (!urlText.isFocused) {
                        urlText.setText(it)
                    }
                }
            }
        }

        // Observe `blockingInfoMap` to update the blocking count
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.blockingInfoMap.collect {
                    updateBlockedCount()
                }
            }
        }

        // Observe whenever AdFilter is enabled/disabled
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                filterViewModel.isEnabled.collect {
                    updateBlockedCount()
                }
            }
        }

        // Observe whenever number filters changed
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                filterViewModel.enabledFilterCount.collect {
                    // TODO("Show number of active filters somewhere on UI")
                }
            }
        }

        // Observe whenever filters' settings are changed via `onDirty` flag
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                filterViewModel.onDirty.collect {
                    // Clear cache when there are changes to the filter.
                    // You need to refresh the page manually to make the changes take effect.
                    webView.clearCache(false)
                    viewModel.dirtyBlockingInfo = true
                    updateBlockedCount()
                }
            }
        }

        /*
         Monitor worker's work finished (download/install) to perform the corresponding actions.
         TODO: This work will run in background forever. Remember to cancel it to avoid memory leak.
         */
        @Suppress("UNUSED_VARIABLE")
        val jobWatcher = filter.jobWatcher(
            inBackground = true,
            lifecycleScope = null,
            lifecycle = null,
        )

        /** Setup filter on first run if has not yet done */
        if (!filter.hasInstallation) {
            val map = mapOf(
                "AdGuard Base" to "https://filters.adtidy.org/extension/chromium/filters/2.txt",
                "EasyPrivacy Lite" to "https://filters.adtidy.org/extension/chromium/filters/118_optimized.txt",
                "AdGuard Tracking Protection" to "https://filters.adtidy.org/extension/chromium/filters/3.txt",
                "AdGuard Annoyances" to "https://filters.adtidy.org/extension/chromium/filters/14.txt",
                "AdGuard Chinese" to "https://filters.adtidy.org/extension/chromium/filters/224.txt",
                "NoCoin Filter List" to "https://filters.adtidy.org/extension/chromium/filters/242.txt",
            )
            for ((key, value) in map) {
                filterViewModel.addFilter(key, value)
            }
            AlertDialog
                .Builder(this)
                .setTitle(R.string.filter_download_title)
                .setMessage(R.string.filter_download_msg)
                .setCancelable(true)
                .setPositiveButton(
                    android.R.string.ok,
                ) { _, _ ->
                    val filters = filterViewModel.filters.value
                    for ((key, _) in filters) {
                        filterViewModel.download(key)
                    }
                }.show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val bundle = Bundle()
        webView.saveState(bundle)
        outState.putBundle(KEY_WEB_VIEW, bundle)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        val bundle = savedInstanceState.getBundle(KEY_WEB_VIEW)
        if (bundle != null) {
            webView.restoreState(bundle)
        }
        super.onRestoreInstanceState(savedInstanceState)
    }

    override fun onPageStarted(
        url: String?,
        favicon: Bitmap?,
    ) {
        runOnUiThread {
            url?.let { url -> viewModel.currentPageUrl.update { url } }
            // Clear the counter if filters settings had been changed
            viewModel.clearDirty()
            updateBlockedCount()
        }
    }

    override fun progressChanged(newProgress: Int) {
        runOnUiThread {
            webView.url?.let { url -> viewModel.currentPageUrl.update { url } }
            progressAnimator.progress = newProgress
            /* This doesn't seem right, some WebClient don't use WebChromeClient
            // newProgress increase to 100, but not going through all values, typical values are: 10, 70, 100
            if (newProgress == 10) {
                // Clear the counter if filters settings had been changed
                viewModel.clearDirty()
                updateBlockedCount()
            }
             */
        }
    }

    /**
     * Update blocked count text
     */
    private fun updateBlockedCount() {
        val blockedUrlMap =
            viewModel.blockingInfoMap.value[viewModel.currentPageUrl.value]?.blockedUrlMap
        val blockedCount = blockedUrlMap?.size
        when {
            !filterViewModel.isFilterOn() && !filterViewModel.isCustomFilterEnabled() -> {
                binding.countText.text = getString(R.string.off)
            }
            blockedCount == null -> {
                binding.countText.text = getString(R.string.count_none)
            }
            else -> {
                binding.countText.text = String.format(Locale.getDefault(), "%d", blockedUrlMap.size)
            }
        }
    }

    override fun onShouldInterceptRequest(result: FilterResult) {
        if (filterViewModel.isFilterOn()) viewModel.logRequest(result)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
            return
        }
        super.onBackPressed()
    }

    companion object {
        const val KEY_WEB_VIEW = "KEY_WEB_VIEW"
    }
}
