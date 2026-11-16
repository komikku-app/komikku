package androidx.preference

import android.content.Context
import android.content.SharedPreferences
import app.komikku.desktop.compat.SharedPreferencesDataStore
import app.komikku.desktop.compat.unsupported

/**
 * android-compat: AndroidX `Preference`, as a plain data holder.
 *
 * Desktop has no preference views: a Compose screen reads the hierarchy built by an extension's
 * `setupPreferenceScreen` (title, summary, entries, current value, enabled / visible state) and
 * reports user input back through [callChangeListener] + the type-specific value setter, the same
 * sequence AndroidX's widgets use. Values are persisted through [PreferenceDataStore] (own or the
 * [PreferenceManager]'s) or the manager's [SharedPreferences] (the compat desktop store), once the
 * preference is attached to a hierarchy created by [PreferenceManager.createPreferenceScreen].
 *
 * Differences from AndroidX: no resources (the `@StringRes` overloads throw), no icons, intents or
 * extras; a group attaches children that were added before the group itself was attached (AndroidX
 * requires adding the group to the screen first); dependency state is computed on read.
 */
open class Preference(context: Context) : Comparable<Preference> {

    /** Called when the user changes the value, before it is stored; `false` rejects the change. */
    fun interface OnPreferenceChangeListener {
        fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean
    }

    /** Called when the preference is clicked; `true` if the click was handled. */
    fun interface OnPreferenceClickListener {
        fun onPreferenceClick(preference: Preference): Boolean
    }

    /** Computes the summary from the preference's state (replaces [summary] while set). */
    fun interface SummaryProvider<T : Preference> {
        fun provideSummary(preference: T): CharSequence?
    }

    private val preferenceContext: Context = context

    private var managerRef: PreferenceManager? = null
    private var parentGroup: PreferenceGroup? = null
    private var ownDataStore: PreferenceDataStore? = null
    private var changeListener: OnPreferenceChangeListener? = null
    private var clickListener: OnPreferenceClickListener? = null
    private var enabledFlag = true
    private var titleText: CharSequence? = null
    private var summaryText: CharSequence? = null
    private var provider: SummaryProvider<*>? = null
    private var keyValue: String? = null
    private var visibleFlag = true
    private var defaultValueHolder: Any? = null

    open val context: Context get() = preferenceContext

    open var key: String?
        get() = keyValue
        set(value) {
            keyValue = value
            notifyChanged()
        }

    open var title: CharSequence?
        get() = titleText
        set(value) {
            if (value?.toString() != titleText?.toString()) {
                titleText = value
                notifyChanged()
            }
        }

    open var summary: CharSequence?
        get() {
            @Suppress("UNCHECKED_CAST")
            val current = provider as SummaryProvider<Preference>?
            return if (current != null) current.provideSummary(this) else summaryText
        }
        set(value) {
            check(provider == null) { "Preference already has a SummaryProvider set." }
            if (value?.toString() != summaryText?.toString()) {
                summaryText = value
                notifyChanged()
            }
        }

    var summaryProvider: SummaryProvider<*>?
        get() = provider
        set(value) {
            provider = value
            notifyChanged()
        }

    /**
     * Whether the preference can be used. Like AndroidX, a preference is also disabled while its
     * [dependency] disables dependents or its parent group does.
     */
    open var isEnabled: Boolean
        get() = enabledFlag && dependencyMet() && parentGroup?.shouldDisableDependents() != true
        set(value) {
            if (enabledFlag != value) {
                enabledFlag = value
                notifyDependencyChange(shouldDisableDependents())
                notifyChanged()
            }
        }

    open var isVisible: Boolean
        get() = visibleFlag
        set(value) {
            if (visibleFlag != value) {
                visibleFlag = value
                notifyChanged()
            }
        }

    open var isSelectable: Boolean = true
    open var isPersistent: Boolean = true
    open var isIconSpaceReserved: Boolean = true
    open var isSingleLineTitle: Boolean = true
    open var isCopyingEnabled: Boolean = false
    open var isShouldDisableView: Boolean = true
    open var order: Int = DEFAULT_ORDER
    open var fragment: String? = null
    open var layoutResource: Int = 0
    open var widgetLayoutResource: Int = 0

    /** Key of the preference whose state enables / disables this one. */
    open var dependency: String? = null

    open val preferenceManager: PreferenceManager? get() = managerRef

    open val parent: PreferenceGroup? get() = parentGroup

    /** `null` while a [PreferenceDataStore] is used or the preference is not attached. */
    open val sharedPreferences: SharedPreferences?
        get() = if (preferenceDataStore != null) null else managerRef?.sharedPreferences

    open var preferenceDataStore: PreferenceDataStore?
        get() = ownDataStore ?: managerRef?.preferenceDataStore
        set(value) {
            ownDataStore = value
        }

    /** android-compat: read access for the desktop renderer (AndroidX has no public getter). */
    open val defaultValue: Any? get() = defaultValueHolder

    open fun setDefaultValue(defaultValue: Any?) {
        defaultValueHolder = defaultValue
    }

    open fun setTitle(titleResId: Int): Unit = unsupported("Preference.setTitle(@StringRes)")

    open fun setSummary(summaryResId: Int): Unit = unsupported("Preference.setSummary(@StringRes)")

    fun hasKey(): Boolean = !keyValue.isNullOrEmpty()

    open fun getOnPreferenceChangeListener(): OnPreferenceChangeListener? = changeListener

    open fun setOnPreferenceChangeListener(listener: OnPreferenceChangeListener?) {
        changeListener = listener
    }

    open fun getOnPreferenceClickListener(): OnPreferenceClickListener? = clickListener

    open fun setOnPreferenceClickListener(listener: OnPreferenceClickListener?) {
        clickListener = listener
    }

    /** Asks the change listener whether [newValue] may be stored. */
    open fun callChangeListener(newValue: Any?): Boolean =
        changeListener?.onPreferenceChange(this, newValue) ?: true

    /** Runs a user click: [onClick] (e.g. toggles a switch), then the click listener. */
    open fun performClick() {
        if (!isEnabled || !isSelectable) return
        onClick()
        clickListener?.onPreferenceClick(this)
    }

    /** Whether preferences depending on this one must be disabled. */
    open fun shouldDisableDependents(): Boolean = !isEnabled

    /** Dependents compute their state on read; this only tells the renderer they changed. */
    open fun notifyDependencyChange(disableDependents: Boolean) {
        val ownKey = keyValue ?: return
        val root = root() ?: return
        root.descendants().filter { it.dependency == ownKey }.forEach { it.notifyChanged() }
    }

    open fun shouldPersist(): Boolean = managerRef != null && isPersistent && hasKey()

    override fun compareTo(other: Preference): Int {
        if (order != other.order) return order.compareTo(other.order)
        val a = titleText?.toString()
        val b = other.titleText?.toString()
        return when {
            a == b -> 0
            a == null -> 1
            b == null -> -1
            else -> a.compareTo(b, ignoreCase = true)
        }
    }

    override fun toString(): String = listOfNotNull(titleText, summary).joinToString(" ")

    // ---- Hooks for subclasses ----

    /** Handles a click on the preference itself (dialogs are opened by the renderer). */
    protected open fun onClick() {}

    /**
     * Loads the initial value when the preference is attached: [defaultValue] is `null` when a
     * value is already persisted, so subclasses read `getPersisted*(defaultValue)`.
     */
    protected open fun onSetInitialValue(defaultValue: Any?) {}

    /** Tells the renderer that the preference's state changed. */
    protected open fun notifyChanged() {
        managerRef?.dispatchChanged(this)
    }

    protected open fun findPreferenceInHierarchy(key: String): Preference? =
        root()?.findPreference(key) ?: managerRef?.findPreference(key)

    protected open fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
        managerRef = preferenceManager
        dispatchSetInitialValue()
    }

    // ---- Persistence (AndroidX visibility: string set accessors are public, the rest protected) ----

    protected open fun persistString(value: String?): Boolean {
        if (!shouldPersist()) return false
        if (value != getPersistedString(null)) backingStore().putString(keyValue!!, value)
        return true
    }

    protected open fun getPersistedString(defaultReturnValue: String?): String? =
        if (shouldPersist()) backingStore().getString(keyValue!!, defaultReturnValue) else defaultReturnValue

    open fun persistStringSet(values: Set<String>?): Boolean {
        if (!shouldPersist()) return false
        if (values != getPersistedStringSet(null)) backingStore().putStringSet(keyValue!!, values)
        return true
    }

    open fun getPersistedStringSet(defaultReturnValue: Set<String>?): Set<String>? =
        if (shouldPersist()) backingStore().getStringSet(keyValue!!, defaultReturnValue) else defaultReturnValue

    protected open fun persistInt(value: Int): Boolean {
        if (!shouldPersist()) return false
        if (!isPersisted(value)) backingStore().putInt(keyValue!!, value)
        return true
    }

    protected open fun getPersistedInt(defaultReturnValue: Int): Int =
        if (shouldPersist()) backingStore().getInt(keyValue!!, defaultReturnValue) else defaultReturnValue

    protected open fun persistLong(value: Long): Boolean {
        if (!shouldPersist()) return false
        if (!isPersisted(value)) backingStore().putLong(keyValue!!, value)
        return true
    }

    protected open fun getPersistedLong(defaultReturnValue: Long): Long =
        if (shouldPersist()) backingStore().getLong(keyValue!!, defaultReturnValue) else defaultReturnValue

    protected open fun persistFloat(value: Float): Boolean {
        if (!shouldPersist()) return false
        if (!isPersisted(value)) backingStore().putFloat(keyValue!!, value)
        return true
    }

    protected open fun getPersistedFloat(defaultReturnValue: Float): Float =
        if (shouldPersist()) backingStore().getFloat(keyValue!!, defaultReturnValue) else defaultReturnValue

    protected open fun persistBoolean(value: Boolean): Boolean {
        if (!shouldPersist()) return false
        if (!isPersisted(value)) backingStore().putBoolean(keyValue!!, value)
        return true
    }

    protected open fun getPersistedBoolean(defaultReturnValue: Boolean): Boolean =
        if (shouldPersist()) backingStore().getBoolean(keyValue!!, defaultReturnValue) else defaultReturnValue

    // ---- Internals ----

    internal fun attach(preferenceManager: PreferenceManager) {
        if (managerRef === preferenceManager) return
        onAttachedToHierarchy(preferenceManager)
    }

    internal fun assignParent(group: PreferenceGroup?) {
        check(group == null || parentGroup == null) { "This preference already has a parent." }
        parentGroup = group
    }

    private fun dispatchSetInitialValue() {
        // A data store cannot tell whether a key exists, so its value always wins (as in AndroidX).
        val hasPersisted = shouldPersist() &&
            (preferenceDataStore != null || managerRef?.sharedPreferences?.contains(keyValue!!) == true)
        when {
            hasPersisted -> onSetInitialValue(null)
            defaultValueHolder != null -> onSetInitialValue(defaultValueHolder)
        }
    }

    private fun dependencyMet(): Boolean {
        val dependencyKey = dependency ?: return true
        val target = root()?.findPreference<Preference>(dependencyKey) ?: return true
        return target !== this && !target.shouldDisableDependents()
    }

    private fun root(): PreferenceGroup? {
        var current: PreferenceGroup? = parentGroup ?: (this as? PreferenceGroup)
        while (current?.parent != null) current = current.parent
        return current
    }

    /** Own or the manager's data store, else the manager's shared preferences. */
    private fun backingStore(): PreferenceDataStore =
        preferenceDataStore ?: managerRef!!.sharedPreferencesDataStore

    /** Whether the stored value equals [value] (skips redundant writes, like AndroidX). */
    private fun isPersisted(value: Any): Boolean {
        val store = backingStore()
        val key = keyValue!!
        if (store is SharedPreferencesDataStore && !store.preferences.contains(key)) return false
        return when (value) {
            is Int -> store.getInt(key, value.inv()) == value
            is Long -> store.getLong(key, value.inv()) == value
            is Float -> store.getFloat(key, Float.NaN) == value
            is Boolean -> store.getBoolean(key, !value) == value
            else -> false
        }
    }

    companion object {
        /** Order of preferences that have not been given one; groups order them as added. */
        const val DEFAULT_ORDER = Int.MAX_VALUE
    }
}
