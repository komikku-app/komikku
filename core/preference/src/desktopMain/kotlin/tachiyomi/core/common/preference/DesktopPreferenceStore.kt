package tachiyomi.core.common.preference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import tachiyomi.core.common.desktop.DesktopDirs
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Desktop (JVM) [PreferenceStore] persisted as one JSON file per store.
 *
 * - Values keep their type (`String`, `Int`, `Long`, `Float`, `Boolean`, `Set<String>`), so
 *   [getAll] returns the same kinds of values as Android's `SharedPreferences.getAll()`. That lets
 *   this class back the desktop `SharedPreferences` shim used by extensions later on.
 * - Reads are lock-free (immutable snapshot); writes are serialized, update the in-memory snapshot
 *   and then replace the file atomically (temp file + fsync + rename).
 * - [Preference.changes] emits the current value first and then every distinct change, like the
 *   Android implementation.
 *
 * Use a single instance per file (e.g. through DI): two instances over the same file do not see
 * each other's writes.
 */
class DesktopPreferenceStore(
    val file: File,
) : PreferenceStore {

    /** Store named [name] in the app's preferences directory (see [DesktopDirs]). */
    constructor(name: String = DEFAULT_STORE_NAME) : this(DesktopDirs.preferencesDir.resolve(fileNameFor(name)))

    private val writeLock = Any()
    private val snapshot = MutableStateFlow(load())

    override fun getString(key: String, defaultValue: String): Preference<String> =
        DesktopPreference.StringPrimitive(this, key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> =
        DesktopPreference.LongPrimitive(this, key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> =
        DesktopPreference.IntPrimitive(this, key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
        DesktopPreference.FloatPrimitive(this, key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
        DesktopPreference.BooleanPrimitive(this, key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
        DesktopPreference.StringSetPrimitive(this, key, defaultValue)

    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> = DesktopPreference.ObjectAsString(this, key, defaultValue, serializer, deserializer)

    override fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ): Preference<T> = DesktopPreference.ObjectAsInt(this, key, defaultValue, serializer, deserializer)

    override fun getAll(): Map<String, *> = snapshot.value

    /**
     * Applies several changes with a single file write. Values must be one of the supported types
     * (`String`, `Int`, `Long`, `Float`, `Boolean`, `Set<String>`).
     */
    fun edit(action: MutableMap<String, Any>.() -> Unit) {
        update { current ->
            current.toMutableMap()
                .apply(action)
                .onEach { (key, value) -> requireSupported(key, value) }
        }
    }

    internal fun read(key: String): Any? = snapshot.value[key]

    internal fun write(key: String, value: Any?) {
        if (value != null) requireSupported(key, value)
        update { current ->
            if (value == null) current - key else current + (key to value)
        }
    }

    internal fun changes(key: String): Flow<Any?> = snapshot
        .map { it[key] }
        .distinctUntilChanged()

    private fun update(transform: (Map<String, Any>) -> Map<String, Any>) {
        synchronized(writeLock) {
            val current = snapshot.value
            val updated = transform(current)
            if (updated == current) return
            snapshot.value = updated
            try {
                persist(updated)
            } catch (e: IOException) {
                // Keep the in-memory value (like SharedPreferences.apply()); the next write retries.
                System.err.println("DesktopPreferenceStore: could not write ${file.path}: $e")
            }
        }
    }

    private fun load(): Map<String, Any> {
        tempFile().delete()
        if (!file.exists()) return emptyMap()
        return try {
            decode(file.readText())
        } catch (e: Exception) {
            // Keep the unreadable file for inspection and start empty instead of crashing.
            val backup = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
            file.renameTo(backup)
            System.err.println("DesktopPreferenceStore: unreadable ${file.path} moved to ${backup.name}: $e")
            emptyMap()
        }
    }

    private fun persist(values: Map<String, Any>) {
        file.absoluteFile.parentFile?.mkdirs()
        val tmp = tempFile()
        FileOutputStream(tmp).use { out ->
            out.write(encode(values).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        try {
            Files.move(
                tmp.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun tempFile() = File(file.absoluteFile.parentFile, "${file.name}.tmp")

    companion object {
        const val DEFAULT_STORE_NAME = "preferences"

        private const val FORMAT_VERSION = 1
        private const val KEY_VERSION = "version"
        private const val KEY_VALUES = "values"
        private const val KEY_TYPE = "type"
        private const val KEY_VALUE = "value"

        private const val TYPE_STRING = "string"
        private const val TYPE_INT = "int"
        private const val TYPE_LONG = "long"
        private const val TYPE_FLOAT = "float"
        private const val TYPE_BOOLEAN = "boolean"
        private const val TYPE_STRING_SET = "string_set"

        private val json = Json { prettyPrint = true }

        /** File name used for the store [name]; characters unsafe in file names are replaced. */
        fun fileNameFor(name: String): String {
            require(name.isNotBlank()) { "Preference store name must not be blank" }
            return name.replace(UNSAFE_FILE_NAME_CHARS, "_") + ".json"
        }

        private val UNSAFE_FILE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")

        private fun requireSupported(key: String, value: Any) {
            val supported = when (value) {
                is String, is Int, is Long, is Float, is Boolean -> true
                is Set<*> -> value.all { it is String }
                else -> false
            }
            require(supported) { "Unsupported preference value type for $key: ${value::class.qualifiedName}" }
        }

        internal fun encode(values: Map<String, Any>): String {
            val entries = values.toSortedMap().mapValues { (_, value) ->
                val (type, element) = when (value) {
                    is String -> TYPE_STRING to JsonPrimitive(value)
                    is Int -> TYPE_INT to JsonPrimitive(value)
                    is Long -> TYPE_LONG to JsonPrimitive(value)
                    // NaN / Infinity are not valid JSON numbers; store them as strings.
                    is Float -> TYPE_FLOAT to if (value.isFinite()) JsonPrimitive(value) else JsonPrimitive(value.toString())
                    is Boolean -> TYPE_BOOLEAN to JsonPrimitive(value)
                    is Set<*> -> TYPE_STRING_SET to JsonArray(value.map { JsonPrimitive(it as String) })
                    else -> error("Unsupported preference value type: ${value::class.qualifiedName}")
                }
                JsonObject(mapOf(KEY_TYPE to JsonPrimitive(type), KEY_VALUE to element))
            }
            val root = JsonObject(
                mapOf(
                    KEY_VERSION to JsonPrimitive(FORMAT_VERSION),
                    KEY_VALUES to JsonObject(entries),
                ),
            )
            return json.encodeToString(JsonElement.serializer(), root) + "\n"
        }

        internal fun decode(text: String): Map<String, Any> {
            val values = json.parseToJsonElement(text).jsonObject[KEY_VALUES]?.jsonObject ?: return emptyMap()
            return buildMap {
                values.forEach { (key, element) ->
                    val entry = element.jsonObject
                    val type = entry[KEY_TYPE]?.jsonPrimitive?.content
                    val value = entry[KEY_VALUE] ?: return@forEach
                    val decoded: Any = when (type) {
                        TYPE_STRING -> value.jsonPrimitive.content
                        TYPE_INT -> value.jsonPrimitive.int
                        TYPE_LONG -> value.jsonPrimitive.long
                        TYPE_FLOAT -> value.jsonPrimitive.content.toFloat()
                        TYPE_BOOLEAN -> value.jsonPrimitive.boolean
                        TYPE_STRING_SET -> value.jsonArray.mapTo(LinkedHashSet()) { it.jsonPrimitive.content }
                        // Written by a newer version: skip rather than fail the whole store.
                        else -> return@forEach
                    }
                    put(key, decoded)
                }
            }
        }
    }
}

sealed class DesktopPreference<T>(
    private val store: DesktopPreferenceStore,
    private val key: String,
    private val defaultValue: T,
) : Preference<T> {

    /** Converts the stored value; throws [ClassCastException] if it has the wrong type. */
    protected abstract fun decode(stored: Any): T

    protected abstract fun encode(value: T): Any

    override fun key(): String = key

    override fun get(): T {
        val stored = store.read(key) ?: return defaultValue
        return try {
            decode(stored)
        } catch (_: ClassCastException) {
            // Same recovery as AndroidPreference: drop a value stored with another type.
            delete()
            defaultValue
        }
    }

    override fun set(value: T) {
        store.write(key, encode(value))
    }

    override fun isSet(): Boolean = store.read(key) != null

    override fun delete() {
        store.write(key, null)
    }

    override fun defaultValue(): T = defaultValue

    override fun changes(): Flow<T> = store.changes(key)
        .map { get() }
        .conflate()

    override fun stateIn(scope: CoroutineScope): StateFlow<T> {
        return changes().stateIn(scope, SharingStarted.Eagerly, get())
    }

    class StringPrimitive(
        store: DesktopPreferenceStore,
        key: String,
        defaultValue: String,
    ) : DesktopPreference<String>(store, key, defaultValue) {
        override fun decode(stored: Any): String = stored as String
        override fun encode(value: String): Any = value
    }

    class LongPrimitive(
        store: DesktopPreferenceStore,
        key: String,
        defaultValue: Long,
    ) : DesktopPreference<Long>(store, key, defaultValue) {
        override fun decode(stored: Any): Long = stored as Long
        override fun encode(value: Long): Any = value
    }

    class IntPrimitive(
        store: DesktopPreferenceStore,
        key: String,
        defaultValue: Int,
    ) : DesktopPreference<Int>(store, key, defaultValue) {
        override fun decode(stored: Any): Int = stored as Int
        override fun encode(value: Int): Any = value
    }

    class FloatPrimitive(
        store: DesktopPreferenceStore,
        key: String,
        defaultValue: Float,
    ) : DesktopPreference<Float>(store, key, defaultValue) {
        override fun decode(stored: Any): Float = stored as Float
        override fun encode(value: Float): Any = value
    }

    class BooleanPrimitive(
        store: DesktopPreferenceStore,
        key: String,
        defaultValue: Boolean,
    ) : DesktopPreference<Boolean>(store, key, defaultValue) {
        override fun decode(stored: Any): Boolean = stored as Boolean
        override fun encode(value: Boolean): Any = value
    }

    class StringSetPrimitive(
        store: DesktopPreferenceStore,
        key: String,
        defaultValue: Set<String>,
    ) : DesktopPreference<Set<String>>(store, key, defaultValue) {
        @Suppress("UNCHECKED_CAST")
        override fun decode(stored: Any): Set<String> = stored as Set<String>
        override fun encode(value: Set<String>): Any = value.toSet()
    }

    class ObjectAsString<T>(
        store: DesktopPreferenceStore,
        key: String,
        defaultValue: T,
        private val serializer: (T) -> String,
        private val deserializer: (String) -> T,
    ) : DesktopPreference<T>(store, key, defaultValue) {
        override fun decode(stored: Any): T {
            val raw = stored as String
            return try {
                deserializer(raw)
            } catch (_: Exception) {
                defaultValue()
            }
        }

        override fun encode(value: T): Any = serializer(value)
    }

    class ObjectAsInt<T>(
        store: DesktopPreferenceStore,
        key: String,
        defaultValue: T,
        private val serializer: (T) -> Int,
        private val deserializer: (Int) -> T,
    ) : DesktopPreference<T>(store, key, defaultValue) {
        override fun decode(stored: Any): T {
            val raw = stored as Int
            return try {
                deserializer(raw)
            } catch (_: Exception) {
                defaultValue()
            }
        }

        override fun encode(value: T): Any = serializer(value)
    }
}
