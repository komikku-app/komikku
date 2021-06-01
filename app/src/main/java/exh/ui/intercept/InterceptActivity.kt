package exh.ui.intercept

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onCancel
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Chapter
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.databinding.EhActivityInterceptBinding
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.ui.base.activity.BaseViewBindingActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.ui.reader.ReaderActivity
import exh.GalleryAddEvent
import exh.GalleryAdder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class InterceptActivity : BaseViewBindingActivity<EhActivityInterceptBinding>() {
    private var statusJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = EhActivityInterceptBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show back button
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        processLink()
    }

    private fun processLink() {
        if (Intent.ACTION_VIEW == intent.action) {
            binding.interceptProgress.isVisible = true
            binding.interceptStatus.setText(R.string.loading_manga)
            loadGallery(intent.dataString!!)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> onBackPressed()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onStart() {
        super.onStart()
        statusJob?.cancel()
        statusJob = status
            .onEach {
                when (it) {
                    is InterceptResult.Success -> {
                        binding.interceptProgress.isVisible = false
                        binding.interceptStatus.setText(R.string.launching_app)
                        onBackPressed()
                        startActivity(
                            if (it.chapter != null) {
                                ReaderActivity.newIntent(this, it.manga, it.chapter)
                            } else {
                                Intent(this, MainActivity::class.java)
                                    .setAction(MainActivity.SHORTCUT_MANGA)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    .putExtra(MangaController.MANGA_EXTRA, it.mangaId)
                            }
                        )
                    }
                    is InterceptResult.Failure -> {
                        binding.interceptProgress.isVisible = false
                        binding.interceptStatus.text = this.getString(R.string.error_with_reason, it.reason)
                        MaterialDialog(this)
                            .title(R.string.chapter_error)
                            .message(text = this.getString(R.string.could_not_open_manga, it.reason))
                            .cancelable(true)
                            .cancelOnTouchOutside(true)
                            .positiveButton(android.R.string.ok)
                            .onCancel { onBackPressed() }
                            .onDismiss { onBackPressed() }
                            .show()
                    }
                }
            }
            .launchIn(lifecycleScope)
    }

    override fun onStop() {
        super.onStop()
        statusJob?.cancel()
    }

    private val galleryAdder = GalleryAdder()

    val status: MutableStateFlow<InterceptResult> = MutableStateFlow(InterceptResult.Idle)

    @Synchronized
    fun loadGallery(gallery: String) {
        // Do not load gallery if already loading
        if (status.value is InterceptResult.Idle) {
            status.value = InterceptResult.Loading
            val sources = galleryAdder.pickSource(gallery)
            if (sources.size > 1) {
                MaterialDialog(this)
                    .title(R.string.label_sources)
                    .listItemsSingleChoice(items = sources.map { it.toString() }) { _, index, _ ->
                        loadGalleryEnd(gallery, sources[index])
                    }
                    .show()
            } else {
                loadGalleryEnd(gallery)
            }
        }
    }

    @Synchronized
    private fun loadGalleryEnd(gallery: String, source: UrlImportableSource? = null) {
        // Load gallery async
        lifecycleScope.launch(Dispatchers.IO) {
            val result = galleryAdder.addGallery(this@InterceptActivity, gallery, forceSource = source)

            status.value = when (result) {
                is GalleryAddEvent.Success -> result.manga.id?.let {
                    InterceptResult.Success(it, result.manga, result.chapter)
                } ?: InterceptResult.Failure(this@InterceptActivity.getString(R.string.manga_id_is_null))
                is GalleryAddEvent.Fail -> InterceptResult.Failure(result.logMessage)
            }
        }
    }
}

sealed class InterceptResult {
    object Idle : InterceptResult()
    object Loading : InterceptResult()
    data class Success(val mangaId: Long, val manga: Manga, val chapter: Chapter? = null) : InterceptResult()
    data class Failure(val reason: String) : InterceptResult()
}
