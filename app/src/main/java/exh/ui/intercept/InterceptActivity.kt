package exh.ui.intercept

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.core.view.isVisible
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onCancel
import com.afollestad.materialdialogs.callbacks.onDismiss
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.EhActivityInterceptBinding
import eu.kanade.tachiyomi.source.online.UrlImportableSource
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController
import exh.GalleryAddEvent
import exh.GalleryAdder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.subjects.BehaviorSubject

class InterceptActivity : BaseActivity<EhActivityInterceptBinding>() {
    private var statusSubscription: Subscription? = null

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
        statusSubscription?.unsubscribe()
        statusSubscription = status
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                when (it) {
                    is InterceptResult.Success -> {
                        binding.interceptProgress.isVisible = false
                        binding.interceptStatus.setText(R.string.launching_app)
                        onBackPressed()
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .setAction(MainActivity.SHORTCUT_MANGA)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                .putExtra(MangaController.MANGA_EXTRA, it.mangaId)
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
    }

    override fun onStop() {
        super.onStop()
        statusSubscription?.unsubscribe()
    }

    private val galleryAdder = GalleryAdder()

    val status: BehaviorSubject<InterceptResult> = BehaviorSubject.create(InterceptResult.Idle)

    @Synchronized
    fun loadGallery(gallery: String) {
        // Do not load gallery if already loading
        if (status.value is InterceptResult.Idle) {
            status.onNext(InterceptResult.Loading)
            val sources = galleryAdder.pickSource(gallery)
            if (sources.size > 1) {
                MaterialDialog(this)
                    .title(R.string.select_source)
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
        scope.launch(Dispatchers.IO) {
            val result = galleryAdder.addGallery(this@InterceptActivity, gallery, forceSource = source)

            status.onNext(
                when (result) {
                    is GalleryAddEvent.Success -> result.manga.id?.let {
                        InterceptResult.Success(it)
                    } ?: InterceptResult.Failure(this@InterceptActivity.getString(R.string.manga_id_is_null))
                    is GalleryAddEvent.Fail -> InterceptResult.Failure(result.logMessage)
                }
            )
        }
    }
}

sealed class InterceptResult {
    object Idle : InterceptResult()
    object Loading : InterceptResult()
    data class Success(val mangaId: Long) : InterceptResult()
    data class Failure(val reason: String) : InterceptResult()
}
