package androidx.preference

import android.content.Context

/**
 * android-compat: AndroidX `PreferenceGroup`, a preference holding child preferences sorted by
 * [Preference.order] (in insertion order while [isOrderingAsAdded]).
 *
 * Children added before the group is attached to a [PreferenceManager] are attached with it.
 * The bottom of this file mirrors the preference-ktx extensions (`androidx.preference.PreferenceGroupKt`).
 */
abstract class PreferenceGroup(context: Context) : Preference(context) {

    private val childList = mutableListOf<Preference>()
    private var nextOrder = 0

    open var isOrderingAsAdded: Boolean = true

    /** Number of children shown before the group is expanded (no collapsing on desktop). */
    open var initialExpandedChildrenCount: Int = Int.MAX_VALUE

    open val preferenceCount: Int get() = synchronized(childList) { childList.size }

    open fun getPreference(index: Int): Preference = synchronized(childList) { childList[index] }

    open fun addItemFromInflater(preference: Preference) {
        addPreference(preference)
    }

    open fun addPreference(preference: Preference): Boolean {
        preference.parent?.takeIf { it !== this }?.removePreference(preference)
        synchronized(childList) {
            if (preference in childList) return true
            if (preference.order == DEFAULT_ORDER && isOrderingAsAdded) {
                preference.order = nextOrder++
            }
            if (preference is PreferenceGroup) preference.isOrderingAsAdded = isOrderingAsAdded
            if (!onPrepareAddPreference(preference)) return false
            // Stable insertion by order: after every child with the same or a lower order.
            val index = childList.indexOfFirst { it.order > preference.order }.takeIf { it >= 0 } ?: childList.size
            childList.add(index, preference)
        }
        preference.assignParent(this)
        preferenceManager?.let(preference::attach)
        notifyChanged()
        return true
    }

    open fun removePreference(preference: Preference): Boolean {
        val removed = synchronized(childList) { childList.remove(preference) }
        if (removed) {
            preference.assignParent(null)
            notifyChanged()
        }
        return removed
    }

    open fun removeAll() {
        val removed = synchronized(childList) { childList.toList().also { childList.clear() } }
        removed.forEach { it.assignParent(null) }
        notifyChanged()
    }

    /** Finds a preference by key in this group and its descendants (the group itself included). */
    open fun <T : Preference> findPreference(key: CharSequence): T? {
        val wanted = key.toString()
        @Suppress("UNCHECKED_CAST")
        if (this.key == wanted) return this as T
        for (child in synchronized(childList) { childList.toList() }) {
            @Suppress("UNCHECKED_CAST")
            if (child.key == wanted) return child as T
            if (child is PreferenceGroup) child.findPreference<T>(wanted)?.let { return it }
        }
        return null
    }

    /** Return `false` to reject [preference] in [addPreference]. */
    protected open fun onPrepareAddPreference(preference: Preference): Boolean = true

    protected open fun isOnSameScreenAsChildren(): Boolean = true

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
        super.onAttachedToHierarchy(preferenceManager)
        synchronized(childList) { childList.toList() }.forEach { it.attach(preferenceManager) }
    }

    /** android-compat: every descendant, depth first. */
    internal fun descendants(): Sequence<Preference> = sequence {
        for (child in synchronized(childList) { childList.toList() }) {
            yield(child)
            if (child is PreferenceGroup) yieldAll(child.descendants())
        }
    }
}

// ---- preference-ktx (`androidx.preference:preference-ktx`, PreferenceGroup.kt) ----

/** Returns the preference with [key], or `null`. */
operator fun <T : Preference> PreferenceGroup.get(key: CharSequence): T? = findPreference(key)

/** Returns the preference at [index]; throws [IndexOutOfBoundsException] if out of range. */
operator fun PreferenceGroup.get(index: Int): Preference = getPreference(index)

/** Whether [preference] is a direct child of this group. */
operator fun PreferenceGroup.contains(preference: Preference): Boolean {
    for (index in 0 until size) {
        if (get(index) == preference) return true
    }
    return false
}

operator fun PreferenceGroup.plusAssign(preference: Preference) {
    addPreference(preference)
}

operator fun PreferenceGroup.minusAssign(preference: Preference) {
    removePreference(preference)
}

inline val PreferenceGroup.size: Int get() = preferenceCount

inline fun PreferenceGroup.isEmpty(): Boolean = size == 0

inline fun PreferenceGroup.isNotEmpty(): Boolean = size != 0

inline fun PreferenceGroup.forEach(action: (preference: Preference) -> Unit) {
    for (index in 0 until size) action(get(index))
}

inline fun PreferenceGroup.forEachIndexed(action: (index: Int, preference: Preference) -> Unit) {
    for (index in 0 until size) action(index, get(index))
}

operator fun PreferenceGroup.iterator(): MutableIterator<Preference> = object : MutableIterator<Preference> {
    private var index = 0
    override fun hasNext() = index < size
    override fun next() = getPreference(index++)
    override fun remove() {
        removePreference(getPreference(--index))
    }
}

val PreferenceGroup.children: Sequence<Preference>
    get() = Sequence { iterator() }
