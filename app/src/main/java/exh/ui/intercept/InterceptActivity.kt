package exh.ui.intercept

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.callbacks.onCancel
import com.afollestad.materialdialogs.callbacks.onDismiss
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.databinding.EhActivityInterceptBinding
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.main.MainActivity
import eu.kanade.tachiyomi.ui.manga.MangaController
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.visible
import exh.GalleryAddEvent
import exh.GalleryAdder
import kotlin.concurrent.thread
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
            binding.interceptProgress.visible()
            binding.interceptStatus.text = "Loading gallery..."
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
                        binding.interceptProgress.gone()
                        binding.interceptStatus.text = "Launching app..."
                        onBackPressed()
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .setAction(MainActivity.SHORTCUT_MANGA)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                .putExtra(MangaController.MANGA_EXTRA, it.mangaId)
                        )
                    }
                    is InterceptResult.Failure -> {
                        binding.interceptProgress.gone()
                        binding.interceptStatus.text = "Error: ${it.reason}"
                        MaterialDialog(this)
                            .title(text = "Error")
                            .message(text = "Could not open this gallery:\n\n${it.reason}")
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

    val status = BehaviorSubject.create<InterceptResult>(InterceptResult.Idle())

    @Synchronized
    fun loadGallery(gallery: String) {
        // Do not load gallery if already loading
        if (status.value is InterceptResult.Idle) {
            status.onNext(InterceptResult.Loading())

            // Load gallery async
            thread {
                val result = galleryAdder.addGallery(gallery)

                status.onNext(
                    when (result) {
                        is GalleryAddEvent.Success -> result.manga.id?.let {
                            InterceptResult.Success(it)
                        } ?: InterceptResult.Failure("Manga ID is null!")
                        is GalleryAddEvent.Fail -> InterceptResult.Failure(result.logMessage)
                    }
                )
            }
        }
    }
}

sealed class InterceptResult {
    class Idle : InterceptResult()
    class Loading : InterceptResult()
    data class Success(val mangaId: Long) : InterceptResult()
    data class Failure(val reason: String) : InterceptResult()
}
