package android.util

/**
 * android-compat: `android.util.LruCache`, implemented from its documented behaviour (extensions such as Kagane keep
 * one; the class was missing, so their sources could not be created).
 *
 * A thread-safe cache of at most [maxSize] units (entries by default, see [sizeOf]); reading or writing an entry makes
 * it the most recently used, and putting past [maxSize] evicts the least recently used ones. [get] of a missing key
 * calls [create]; [entryRemoved] is told of every eviction, removal and replacement. Keys and values cannot be `null`.
 */
open class LruCache<K : Any, V : Any>(maxSize: Int) {

    private val map = LinkedHashMap<K, V>(0, 0.75f, true)

    private var size = 0
    private var maxSize = maxSize
    private var putCount = 0
    private var createCount = 0
    private var evictionCount = 0
    private var hitCount = 0
    private var missCount = 0

    init {
        require(maxSize > 0) { "maxSize <= 0" }
    }

    /** Sets the maximum size and evicts what no longer fits. */
    open fun resize(maxSize: Int) {
        require(maxSize > 0) { "maxSize <= 0" }
        synchronized(this) { this.maxSize = maxSize }
        trimToSize(maxSize)
    }

    /** The value of [key], made the most recently used; created by [create] (and cached) when missing. */
    fun get(key: K): V? {
        synchronized(this) {
            val value = map[key]
            if (value != null) {
                hitCount++
                return value
            }
            missCount++
        }
        // Outside the lock: create() may be slow, and the key may be added meanwhile (then that value wins)
        val created = create(key) ?: return null
        val previous = synchronized(this) {
            createCount++
            map.put(key, created).also { previous ->
                if (previous != null) map[key] = previous else size += safeSizeOf(key, created)
            }
        }
        return if (previous != null) {
            entryRemoved(false, key, created, previous)
            previous
        } else {
            trimToSize(maxSize)
            created
        }
    }

    /** Caches [value] for [key] as the most recently used; returns the value it replaced. */
    fun put(key: K, value: V): V? {
        val previous = synchronized(this) {
            putCount++
            size += safeSizeOf(key, value)
            map.put(key, value)?.also { size -= safeSizeOf(key, it) }
        }
        if (previous != null) entryRemoved(false, key, previous, value)
        trimToSize(maxSize)
        return previous
    }

    /** Evicts least recently used entries until the cache is at most [maxSize]; `-1` empties it. */
    open fun trimToSize(maxSize: Int) {
        while (true) {
            val evicted = synchronized(this) {
                check(size >= 0 && (map.isNotEmpty() || size == 0)) {
                    "${javaClass.name}.sizeOf() is reporting inconsistent results!"
                }
                if (size <= maxSize || map.isEmpty()) return
                val eldest = map.entries.first()
                map.remove(eldest.key)
                size -= safeSizeOf(eldest.key, eldest.value)
                evictionCount++
                eldest
            }
            entryRemoved(true, evicted.key, evicted.value, null)
        }
    }

    /** Removes [key]; returns its value. */
    fun remove(key: K): V? {
        val previous = synchronized(this) {
            map.remove(key)?.also { size -= safeSizeOf(key, it) }
        }
        if (previous != null) entryRemoved(false, key, previous, null)
        return previous
    }

    /**
     * Called (outside the cache's lock) when [oldValue] leaves the cache: [evicted] to make room, else removed by
     * [remove] or replaced by [newValue] ([put], or a value [create]d while another was put).
     */
    protected open fun entryRemoved(evicted: Boolean, key: K, oldValue: V, newValue: V?) {}

    /** The value for a missing [key] in [get], or `null` (the default) to create none. */
    protected open fun create(key: K): V? = null

    /** The size of an entry in the cache's units; 1 by default (the cache then counts entries). */
    protected open fun sizeOf(key: K, value: V): Int = 1

    private fun safeSizeOf(key: K, value: V): Int {
        val size = sizeOf(key, value)
        check(size >= 0) { "Negative size: $key=$value" }
        return size
    }

    /** Evicts every entry (each reported to [entryRemoved]). */
    fun evictAll() = trimToSize(-1)

    @Synchronized fun size(): Int = size

    @Synchronized fun maxSize(): Int = maxSize

    @Synchronized fun hitCount(): Int = hitCount

    @Synchronized fun missCount(): Int = missCount

    @Synchronized fun createCount(): Int = createCount

    @Synchronized fun putCount(): Int = putCount

    @Synchronized fun evictionCount(): Int = evictionCount

    /** A copy of the entries, least recently used first. */
    @Synchronized fun snapshot(): Map<K, V> = LinkedHashMap(map)

    @Synchronized
    override fun toString(): String {
        val accesses = hitCount + missCount
        val hitPercent = if (accesses != 0) 100 * hitCount / accesses else 0
        return "LruCache[maxSize=$maxSize,hits=$hitCount,misses=$missCount,hitRate=$hitPercent%]"
    }
}
