package app.komikku.shared.reader.image

/**
 * KMK: a least-recently-used cache bounded by the summed size of its values (e.g. decoded page bytes).
 * A value larger than the whole budget is not cached. Thread-safe.
 */
class ByteSizedLruCache<K : Any, V : Any>(
    val maxBytes: Long,
    private val sizeOf: (V) -> Long,
    private val onEvicted: (K, V) -> Unit = { _, _ -> },
) {
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)

    var currentBytes: Long = 0
        private set

    @Synchronized
    operator fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V) {
        val size = sizeOf(value)
        map.remove(key)?.let {
            currentBytes -= sizeOf(it)
            if (it !== value) onEvicted(key, it)
        }
        if (size > maxBytes) return
        map[key] = value
        currentBytes += size
        trim()
    }

    @Synchronized
    fun remove(key: K): V? = map.remove(key)?.also { currentBytes -= sizeOf(it) }

    @Synchronized
    fun clear() {
        map.forEach { (k, v) -> onEvicted(k, v) }
        map.clear()
        currentBytes = 0
    }

    @get:Synchronized
    val size: Int get() = map.size

    @get:Synchronized
    val keys: List<K> get() = map.keys.toList()

    private fun trim() {
        val iterator = map.entries.iterator()
        while (currentBytes > maxBytes && iterator.hasNext()) {
            val (key, value) = iterator.next()
            iterator.remove()
            currentBytes -= sizeOf(value)
            onEvicted(key, value)
        }
    }
}
