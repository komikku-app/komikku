package androidx.preference

import android.content.Context
import android.content.SharedPreferences
import app.komikku.desktop.compat.SharedPreferencesDataStore
import tachiyomi.core.common.preference.DesktopPreferenceStore
import java.util.concurrent.CopyOnWriteArrayList

/**
 * android-compat: AndroidX `PreferenceManager`.
 *
 * The static helpers resolve the app's main desktop preference store
 * ([DesktopPreferenceStore.DEFAULT_STORE_NAME]), just as Android's default shared preferences back
 * the app's main `PreferenceStore`.
 *
 * An instance owns a preference hierarchy: [createPreferenceScreen] returns a screen whose
 * preferences persist to [preferenceDataStore] if set, else to the shared preferences named
 * [sharedPreferencesName] (for an extension: `ConfigurableSource.preferenceKey()`).
 * [addPreferenceChangeObserver] is an android-compat addition for the desktop renderer.
 */
class PreferenceManager(context: Context) {

    /** android-compat: notified whenever a preference of this hierarchy changes state. */
    fun interface PreferenceChangeObserver {
        fun onPreferenceStateChanged(preference: Preference)
    }

    val context: Context = context

    private val observers = CopyOnWriteArrayList<PreferenceChangeObserver>()
    private var preferences: SharedPreferences? = null
    private var preferencesStore: SharedPreferencesDataStore? = null

    var sharedPreferencesName: String = getDefaultSharedPreferencesName(context)
        set(value) {
            field = value
            preferences = null
            preferencesStore = null
        }

    var sharedPreferencesMode: Int = Context.MODE_PRIVATE

    /** Storage used instead of [sharedPreferences] when set. */
    var preferenceDataStore: PreferenceDataStore? = null

    /** The root of the hierarchy, if one was set with [setPreferences]. */
    var preferenceScreen: PreferenceScreen? = null
        private set

    /** `null` while a [preferenceDataStore] is set, as in AndroidX. */
    val sharedPreferences: SharedPreferences?
        get() = if (preferenceDataStore != null) null else backingPreferences()

    internal val sharedPreferencesDataStore: SharedPreferencesDataStore
        get() = preferencesStore ?: SharedPreferencesDataStore(backingPreferences()).also { preferencesStore = it }

    fun createPreferenceScreen(context: Context): PreferenceScreen =
        PreferenceScreen(context).also { it.attach(this) }

    /** Sets the root of the hierarchy; returns `true` if it changed. */
    fun setPreferences(preferenceScreen: PreferenceScreen?): Boolean {
        if (preferenceScreen === this.preferenceScreen) return false
        this.preferenceScreen = preferenceScreen
        preferenceScreen?.attach(this)
        return true
    }

    fun <T : Preference> findPreference(key: CharSequence): T? = preferenceScreen?.findPreference(key)

    fun addPreferenceChangeObserver(observer: PreferenceChangeObserver) {
        observers.addIfAbsent(observer)
    }

    fun removePreferenceChangeObserver(observer: PreferenceChangeObserver) {
        observers.remove(observer)
    }

    internal fun dispatchChanged(preference: Preference) {
        observers.forEach { it.onPreferenceStateChanged(preference) }
    }

    private fun backingPreferences(): SharedPreferences =
        preferences ?: context.getSharedPreferences(sharedPreferencesName, sharedPreferencesMode)
            .also { preferences = it }

    companion object {
        @JvmStatic
        fun getDefaultSharedPreferences(context: Context): SharedPreferences =
            context.getSharedPreferences(getDefaultSharedPreferencesName(context), Context.MODE_PRIVATE)

        @JvmStatic
        fun getDefaultSharedPreferencesName(@Suppress("UNUSED_PARAMETER") context: Context): String =
            DesktopPreferenceStore.DEFAULT_STORE_NAME
    }
}
