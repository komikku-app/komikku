package android.app

import android.content.ContextWrapper
import android.os.Bundle
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * What extensions that track the current activity use (Keiyoushi's WebView helpers, HentaiCube): registering
 * lifecycle callbacks on the [Application], and [Activity] as a `ContextWrapper`.
 */
class ActivityLifecycleTest {

    @TempDir
    lateinit var dir: File

    private class Callbacks : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    @Test
    fun `callbacks register and unregister`() {
        val application = Application(dir)
        val callbacks = Callbacks()

        application.registerActivityLifecycleCallbacks(callbacks)
        application.unregisterActivityLifecycleCallbacks(callbacks)
    }

    @Test
    fun `an activity is a context wrapper that can finish`() {
        val activity = object : Activity() {}

        activity.shouldBeInstanceOf<ContextWrapper>()
        activity.isFinishing() shouldBe false
        activity.finish()
        activity.isFinishing() shouldBe true
        activity.isDestroyed() shouldBe false
    }

    @Test
    fun `bundle stores values by key`() {
        val bundle = Bundle().apply {
            putString("url", "https://example.org")
            putInt("page", 2)
            putBoolean("nsfw", true)
        }

        bundle.getString("url") shouldBe "https://example.org"
        bundle.getInt("page") shouldBe 2
        bundle.getBoolean("nsfw") shouldBe true
        bundle.getLong("missing", 7L) shouldBe 7L
        Bundle(bundle).size() shouldBe 3
        bundle.remove("url")
        bundle.containsKey("url") shouldBe false
    }
}
