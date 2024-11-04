package exh.ui.intercept

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.presentation.components.AppBar
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import eu.kanade.tachiyomi.util.view.setComposeContent
import exh.GalleryAddEvent
import exh.GalleryAdder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import tachiyomi.core.common.Constants
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.i18n.MR
import tachiyomi.i18n.sy.SYMR
import tachiyomi.presentation.core.components.material.Scaffold
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class InterceptActivity : BaseActivity() {
    private var statusJob: Job? = null

    private val status: MutableStateFlow<InterceptResult> = MutableStateFlow(InterceptResult.Idle)

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.shared_axis_x_push_enter,
                R.anim.shared_axis_x_push_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_push_enter, R.anim.shared_axis_x_push_exit)
        }
        super.onCreate(savedInstanceState)

        setComposeContent {
            InterceptActivityContent(status.collectAsState().value)
        }

        processLink()
    }

    @Composable
    private fun InterceptActivityContent(status: InterceptResult) {
        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = stringResource(MR.strings.app_name),
                    navigateUp = ::finish,
                    scrollBehavior = scrollBehavior,
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                when (status) {
                    InterceptResult.Idle, InterceptResult.Loading -> {
                        Text(
                            text = stringResource(SYMR.strings.loading_entry),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        CircularProgressIndicator(modifier = Modifier.size(56.dp))
                    }
                    is InterceptResult.Success -> Text(
                        text = stringResource(SYMR.strings.launching_app),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    is InterceptResult.Failure -> Text(
                        text = stringResource(SYMR.strings.error_with_reason, status.reason),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        }
    }

    private fun processLink() {
        if (Intent.ACTION_VIEW == intent.action) {
            lifecycleScope.launchIO {
                // wait for sources to load
                Injekt.get<SourceManager>().isInitialized.first { it }
                loadGallery(intent.dataString!!)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        statusJob?.cancel()
        statusJob = status
            .onEach {
                when (it) {
                    is InterceptResult.Success -> {
                        finish()
                        startActivity(
                            if (it.chapter != null) {
                                ReaderActivity.newIntent(this, it.manga.id, it.chapter.id)
                            } else {
                                Intent(this, MainActivity::class.java)
                                    .setAction(Constants.SHORTCUT_MANGA)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    .putExtra(Constants.MANGA_EXTRA, it.mangaId)
                            },
                        )
                    }
                    is InterceptResult.Failure -> {
                        MaterialAlertDialogBuilder(this)
                            .setTitle(MR.strings.chapter_error.getString(this))
                            .setMessage(stringResource(SYMR.strings.could_not_open_entry, it.reason))
                            .setPositiveButton(MR.strings.action_ok.getString(this), null)
                            .setOnCancelListener { finish() }
                            .setOnDismissListener { finish() }
                            .show()
                    }
                    else -> Unit
                }
            }
            .launchIn(lifecycleScope)
    }

    override fun onStop() {
        super.onStop()
        statusJob?.cancel()
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.shared_axis_x_pop_enter,
                R.anim.shared_axis_x_pop_exit,
            )
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.shared_axis_x_pop_enter, R.anim.shared_axis_x_pop_exit)
        }
    }

    private val galleryAdder = GalleryAdder()

    suspend fun loadGallery(gallery: String) {
        // Do not load gallery if already loading
        if (status.value is InterceptResult.Idle) {
            status.value = InterceptResult.Loading
            val sources = galleryAdder.pickSource(gallery)
            if (sources.size > 1) {
                withUIContext {
                    MaterialAlertDialogBuilder(this@InterceptActivity)
                        .setTitle(MR.strings.label_sources.getString(this@InterceptActivity))
                        .setSingleChoiceItems(sources.map { it.toString() }.toTypedArray(), 0) { dialog, index ->
                            dialog.dismiss()
                            lifecycleScope.launchIO {
                                loadGalleryEnd(gallery, sources[index])
                            }
                        }
                        .show()
                }
            } else {
                loadGalleryEnd(gallery)
            }
        }
    }

    private suspend fun loadGalleryEnd(gallery: String, source: UrlImportableSource? = null) {
        val result = galleryAdder.addGallery(this@InterceptActivity, gallery, forceSource = source)

        status.value = when (result) {
            is GalleryAddEvent.Success -> InterceptResult.Success(result.manga.id, result.manga, result.chapter)
            is GalleryAddEvent.Fail -> InterceptResult.Failure(result.logMessage)
        }
    }

    init {
        registerSecureActivity(this)
    }
}

sealed class InterceptResult {
    data object Idle : InterceptResult()
    data object Loading : InterceptResult()
    data class Success(val mangaId: Long, val manga: Manga, val chapter: Chapter? = null) : InterceptResult()
    data class Failure(val reason: String) : InterceptResult()
}
