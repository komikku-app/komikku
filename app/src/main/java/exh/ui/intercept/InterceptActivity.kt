package exh.ui.intercept

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.kanade.domain.chapter.model.Chapter
import eu.kanade.domain.manga.model.Manga
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.EhActivityInterceptBinding
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
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

class InterceptActivity : BaseActivity() {
    private var statusJob: Job? = null

    lateinit var binding: EhActivityInterceptBinding

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
                                ReaderActivity.newIntent(this, it.manga.id, it.chapter.id)
                            } else {
                                Intent(this, MainActivity::class.java)
                                    .setAction(MainActivity.SHORTCUT_MANGA)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                    .putExtra(MangaController.MANGA_EXTRA, it.mangaId)
                            },
                        )
                    }
                    is InterceptResult.Failure -> {
                        binding.interceptProgress.isVisible = false
                        binding.interceptStatus.text = getString(R.string.error_with_reason, it.reason)
                        MaterialAlertDialogBuilder(this)
                            .setTitle(R.string.chapter_error)
                            .setMessage(getString(R.string.could_not_open_manga, it.reason))
                            .setPositiveButton(android.R.string.ok, null)
                            .setOnCancelListener { onBackPressed() }
                            .setOnDismissListener { onBackPressed() }
                            .show()
                    }
                    InterceptResult.Idle -> Unit
                    InterceptResult.Loading -> Unit
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
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.label_sources)
                    .setSingleChoiceItems(sources.map { it.toString() }.toTypedArray(), 0) { dialog, index ->
                        dialog.dismiss()
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
                is GalleryAddEvent.Success -> InterceptResult.Success(result.manga.id, result.manga, result.chapter)
                is GalleryAddEvent.Fail -> InterceptResult.Failure(result.logMessage)
            }
        }
    }

    init {
        registerSecureActivity(this)
    }
}

sealed class InterceptResult {
    object Idle : InterceptResult()
    object Loading : InterceptResult()
    data class Success(val mangaId: Long, val manga: Manga, val chapter: Chapter? = null) : InterceptResult()
    data class Failure(val reason: String) : InterceptResult()
}
