package exh.ui.login

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.input
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.EhActivityLoginBinding
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.activity.BaseViewBindingActivity
import eu.kanade.tachiyomi.util.lang.launchUI
import eu.kanade.tachiyomi.util.system.WebViewUtil
import eu.kanade.tachiyomi.util.system.setDefaultSettings
import eu.kanade.tachiyomi.util.system.toast
import exh.log.xLogD
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import reactivecircus.flowbinding.appcompat.navigationClicks
import uy.kohesive.injekt.injectLazy
import java.net.HttpCookie
import java.util.Locale

/**
 * LoginController
 */
class EhLoginActivity : BaseViewBindingActivity<EhActivityLoginBinding>() {
    val preferenceManager: PreferencesHelper by injectLazy()

    val sourceManager: SourceManager by injectLazy()

    private var bundle: Bundle? = null

    private var igneous: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!WebViewUtil.supportsWebView(this)) {
            toast(R.string.information_webview_required, Toast.LENGTH_LONG)
            finish()
            return
        }

        try {
            binding = EhActivityLoginBinding.inflate(layoutInflater)
            setContentView(binding.root)
        } catch (e: Throwable) {
            // Potentially throws errors like "Error inflating class android.webkit.WebView"
            toast(R.string.information_webview_required, Toast.LENGTH_LONG)
            finish()
            return
        }

        title = "ExHentai login"

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.toolbar.navigationClicks()
            .onEach { finish() }
            .launchIn(lifecycleScope)

        onViewCreated()

        if (bundle == null) {
            binding.webview.setDefaultSettings()

            // Debug mode (chrome://inspect/#devices)
            if (BuildConfig.DEBUG && 0 != applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) {
                WebView.setWebContentsDebuggingEnabled(true)
            }

            binding.webview.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    binding.progressBar.isVisible = true
                    binding.progressBar.progress = newProgress
                    if (newProgress == 100) {
                        binding.progressBar.isInvisible = true
                    }
                    super.onProgressChanged(view, newProgress)
                }
            }
        } else {
            binding.webview.restoreState(bundle!!)
        }

        if (bundle == null) {
            startWebview()
        }
    }

    fun onViewCreated() {
        binding.btnCancel.setOnClickListener { finish() }

        binding.btnAdvanced.setOnClickListener {
            binding.advancedOptions.isVisible = true
            binding.webview.isVisible = false
            binding.btnAdvanced.isEnabled = false
            binding.btnCancel.isEnabled = false
        }

        binding.btnClose.setOnClickListener {
            hideAdvancedOptions()
        }

        binding.btnRecheck.setOnClickListener {
            hideAdvancedOptions()
            binding.webview.loadUrl("https://exhentai.org/")
        }

        binding.btnAltLogin.setOnClickListener {
            hideAdvancedOptions()
            binding.webview.loadUrl("https://e-hentai.org/bounce_login.php")
        }

        binding.btnSkipRestyle.setOnClickListener {
            hideAdvancedOptions()
            binding.webview.loadUrl("https://forums.e-hentai.org/index.php?act=Login&$PARAM_SKIP_INJECT=true")
        }

        binding.btnIgneousCookie.setOnClickListener {
            hideAdvancedOptions()
            openIgneousDialog()
        }

        CookieManager.getInstance().removeAllCookies {
            launchUI {
                if (bundle == null) {
                    startWebview()
                }
            }
        }
    }

    private fun openIgneousDialog() {
        var igneous: CharSequence? = null
        MaterialDialog(this)
            .title(R.string.custom_igneous_cookie)
            .message(R.string.custom_igneous_cookie_message)
            .input { _, charSequence ->
                igneous = charSequence
            }
            .positiveButton(android.R.string.ok) {
                if (!igneous.isNullOrBlank()) {
                    this.igneous = igneous?.toString()?.trim()
                }
            }
            .negativeButton(android.R.string.cancel)
            .show()
    }

    private fun hideAdvancedOptions() {
        binding.advancedOptions.isVisible = false
        binding.webview.isVisible = true
        binding.btnAdvanced.isEnabled = true
        binding.btnCancel.isEnabled = true
    }

    private fun startWebview() {
        binding.webview.setDefaultSettings()

        binding.webview.loadUrl("https://forums.e-hentai.org/index.php?act=Login")

        binding.webview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                xLogD(url)
                val parsedUrl = Uri.parse(url)
                if (parsedUrl.host.equals("forums.e-hentai.org", ignoreCase = true)) {
                    // Hide distracting content
                    if (!parsedUrl.queryParameterNames.contains(PARAM_SKIP_INJECT)) {
                        view.evaluateJavascript(HIDE_JS, null)
                    }
                    // Check login result

                    if (parsedUrl.getQueryParameter("code")?.toInt() != 0) {
                        if (checkLoginCookies(url)) view.loadUrl("https://exhentai.org/")
                    }
                } else if (parsedUrl.host.equals("exhentai.org", ignoreCase = true)) {
                    // At ExHentai, check that everything worked out...
                    if (applyExHentaiCookies(url)) {
                        preferenceManager.enableExhentai().set(true)
                        setResult(RESULT_OK)
                        finish()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        binding.webview?.destroy()
        super.onDestroy()
    }

    /**
     * Check if we are logged in
     */
    fun checkLoginCookies(url: String): Boolean {
        getCookies(url)?.let { parsed ->
            return parsed.filter {
                (
                    it.name.equals(MEMBER_ID_COOKIE, ignoreCase = true) ||
                        it.name.equals(PASS_HASH_COOKIE, ignoreCase = true)
                    ) &&
                    it.value.isNotBlank()
            }.count() >= 2
        }
        return false
    }

    /**
     * Parse cookies at ExHentai
     */
    fun applyExHentaiCookies(url: String): Boolean {
        getCookies(url)?.let { parsed ->

            var memberId: String? = null
            var passHash: String? = null
            var igneous: String? = null

            parsed.forEach {
                when (it.name.lowercase(Locale.getDefault())) {
                    MEMBER_ID_COOKIE -> memberId = it.value
                    PASS_HASH_COOKIE -> passHash = it.value
                    IGNEOUS_COOKIE -> igneous = this.igneous ?: it.value
                }
            }

            // Missing a cookie
            if (memberId == null || passHash == null || igneous == null) return false

            // Update prefs
            preferenceManager.memberIdVal().set(memberId!!)
            preferenceManager.passHashVal().set(passHash!!)
            preferenceManager.igneousVal().set(igneous!!)

            return true
        }
        return false
    }

    private fun getCookies(url: String): List<HttpCookie>? =
        CookieManager.getInstance().getCookie(url)?.let { cookie ->
            cookie.split("; ").flatMap {
                HttpCookie.parse(it)
            }
        }

    companion object {
        const val PARAM_SKIP_INJECT = "TEH_SKIP_INJECT"

        const val MEMBER_ID_COOKIE = "ipb_member_id"
        const val PASS_HASH_COOKIE = "ipb_pass_hash"
        const val IGNEOUS_COOKIE = "igneous"

        const val HIDE_JS =
            """
                    javascript:(function () {
                        document.getElementsByTagName('body')[0].style.visibility = 'hidden';
                        document.getElementsByName('submit')[0].style.visibility = 'visible';
                        document.querySelector('td[width="60%"][valign="top"]').style.visibility = 'visible';

                        function hide(e) {if(e != null) e.style.display = 'none';}

                        hide(document.querySelector(".errorwrap"));
                        hide(document.querySelector('td[width="40%"][valign="top"]'));
                        var child = document.querySelector(".page").querySelector('div');
                        child.style.padding = null;
                        var ft = child.querySelectorAll('table');
                        var fd = child.parentNode.querySelectorAll('div > div');
                        var fh = document.querySelector('#border').querySelectorAll('td > table');
                        hide(ft[0]);
                        hide(ft[1]);
                        hide(fd[1]);
                        hide(fd[2]);
                        hide(child.querySelector('br'));
                        var error = document.querySelector(".page > div > .borderwrap");
                        if(error != null) error.style.visibility = 'visible';
                        hide(fh[0]);
                        hide(fh[1]);
                        hide(document.querySelector("#gfooter"));
                        hide(document.querySelector(".copyright"));
                        document.querySelectorAll("td").forEach(function(e) {
                            e.style.color = "white";
                        });
                        var pc = document.querySelector(".postcolor");
                        if(pc != null) pc.style.color = "#26353F";
                    })()
                    """

        fun newIntent(context: Context): Intent {
            return Intent(context, EhLoginActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }
}
