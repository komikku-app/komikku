package tachiyomi.core.common.preference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.util.prefs.PreferenceChangeListener
import java.util.prefs.Preferences as JavaPreferences

/**
 * Desktop (JVM) [PreferenceStore] backed by [java.util.prefs.Preferences].
 *
 * This is the desktop counterpart of `AndroidPreferenceStore` and lets the shared domain layer
 * persist preferences on Windows/macOS/Linux without any Android dependency.
 */
class DesktopPreferenceStore(
    private val preferences: JavaPreferences = JavaPreferences.userRoot().node("app/komikku"),
) : PreferenceStore {

    private val keyFlow: Flow<String?> = callbackFlow {
        val listener = PreferenceChangeListener { event -> trySend(event.key) }
        preferences.addPreferenceChangeListener(listener)
        awaitClose { preferences.removePreferenceChangeListener(listener) }
    }

    override fun getString(key: String, defaultValue: String): Preference<String> =
        DesktopPreference.StringPrimitive(preferences, keyFlow, key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> =
        DesktopPreference.LongPrimitive(preferences, keyFlow, key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> =
        DesktopPreference.IntPrimitive(preferences, keyFlow, key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
        DesktopPreference.FloatPrimitive(preferences, keyFlow, key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
        DesktopPreference.BooleanPrimitive(preferences, keyFlow, key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
        DesktopPreference.StringSetPrimitive(preferences, keyFlow, key, defaultValue)

    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> = DesktopPreference.ObjectAsString(
        preferences = preferences,
        keyFlow = keyFlow,
        key = key,
        defaultValue = defaultValue,
        serializer = serializer,
        deserializer = deserializer,
    )

    override fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ): Preference<T> = DesktopPreference.ObjectAsInt(
        preferences = preferences,
        keyFlow = keyFlow,
        key = key,
        defaultValue = defaultValue,
        serializer = serializer,
        deserializer = deserializer,
    )

    override fun getAll(): Map<String, *> {
        return preferences.keys().associateWith { preferences.get(it, null) }
    }
}

sealed class DesktopPreference<T>(
    private val preferences: JavaPreferences,
    private val keyFlow: Flow<String?>,
    private val key: String,
    private val defaultValue: T,
) : Preference<T> {

    abstract fun read(preferences: JavaPreferences, key: String, defaultValue: T): T

    abstract fun write(preferences: JavaPreferences, key: String, value: T)

    override fun key(): String = key

    override fun get(): T = try {
        read(preferences, key, defaultValue)
    } catch (_: Exception) {
        delete()
        defaultValue
    }

    override fun set(value: T) {
        write(preferences, key, value)
        preferences.flush()
    }

    override fun isSet(): Boolean = preferences.get(key, null) != null

    override fun delete() {
        preferences.remove(key)
        preferences.flush()
    }

    override fun defaultValue(): T = defaultValue

    override fun changes(): Flow<T> = keyFlow
        .filter { it == key || it == null }
        .onStart { emit("ignition") }
        .map { get() }
        .conflate()

    override fun stateIn(scope: CoroutineScope): StateFlow<T> {
        return changes().stateIn(scope, SharingStarted.Eagerly, get())
    }

    class StringPrimitive(
        preferences: JavaPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: String,
    ) : DesktopPreference<String>(preferences, keyFlow, key, defaultValue) {
        override fun read(preferences: JavaPreferences, key: String, defaultValue: String): String =
            preferences.get(key, defaultValue)

        override fun write(preferences: JavaPreferences, key: String, value: String) {
            preferences.put(key, value)
        }
    }

    class LongPrimitive(
        preferences: JavaPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: Long,
    ) : DesktopPreference<Long>(preferences, keyFlow, key, defaultValue) {
        override fun read(preferences: JavaPreferences, key: String, defaultValue: Long): Long =
            preferences.getLong(key, defaultValue)

        override fun write(preferences: JavaPreferences, key: String, value: Long) {
            preferences.putLong(key, value)
        }
    }

    class IntPrimitive(
        preferences: JavaPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: Int,
    ) : DesktopPreference<Int>(preferences, keyFlow, key, defaultValue) {
        override fun read(preferences: JavaPreferences, key: String, defaultValue: Int): Int =
            preferences.getInt(key, defaultValue)

        override fun write(preferences: JavaPreferences, key: String, value: Int) {
            preferences.putInt(key, value)
        }
    }

    class FloatPrimitive(
        preferences: JavaPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: Float,
    ) : DesktopPreference<Float>(preferences, keyFlow, key, defaultValue) {
        override fun read(preferences: JavaPreferences, key: String, defaultValue: Float): Float =
            preferences.getFloat(key, defaultValue)

        override fun write(preferences: JavaPreferences, key: String, value: Float) {
            preferences.putFloat(key, value)
        }
    }

    class BooleanPrimitive(
        preferences: JavaPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: Boolean,
    ) : DesktopPreference<Boolean>(preferences, keyFlow, key, defaultValue) {
        override fun read(preferences: JavaPreferences, key: String, defaultValue: Boolean): Boolean =
            preferences.getBoolean(key, defaultValue)

        override fun write(preferences: JavaPreferences, key: String, value: Boolean) {
            preferences.putBoolean(key, value)
        }
    }

    class StringSetPrimitive(
        preferences: JavaPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: Set<String>,
    ) : DesktopPreference<Set<String>>(preferences, keyFlow, key, defaultValue) {
        override fun read(preferences: JavaPreferences, key: String, defaultValue: Set<String>): Set<String> {
            val raw = preferences.get(key, null) ?: return defaultValue
            return if (raw.isEmpty()) emptySet() else raw.split(SEPARATOR).toSet()
        }

        override fun write(preferences: JavaPreferences, key: String, value: Set<String>) {
            preferences.put(key, value.joinToString(SEPARATOR))
        }

        private companion object {
            // ASCII unit separator, unlikely to appear inside a preference value.
            const val SEPARATOR = "\u001F"
        }
    }

    class ObjectAsString<T>(
        preferences: JavaPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: T,
        private val serializer: (T) -> String,
        private val deserializer: (String) -> T,
    ) : DesktopPreference<T>(preferences, keyFlow, key, defaultValue) {
        override fun read(preferences: JavaPreferences, key: String, defaultValue: T): T {
            return try {
                preferences.get(key, null)?.let(deserializer) ?: defaultValue
            } catch (_: Exception) {
                defaultValue
            }
        }

        override fun write(preferences: JavaPreferences, key: String, value: T) {
            preferences.put(key, serializer(value))
        }
    }

    class ObjectAsInt<T>(
        preferences: JavaPreferences,
        keyFlow: Flow<String?>,
        key: String,
        defaultValue: T,
        private val serializer: (T) -> Int,
        private val deserializer: (Int) -> T,
    ) : DesktopPreference<T>(preferences, keyFlow, key, defaultValue) {
        override fun read(preferences: JavaPreferences, key: String, defaultValue: T): T {
            return try {
                if (preferences.get(key, null) != null) {
                    deserializer(preferences.getInt(key, 0))
                } else {
                    defaultValue
                }
            } catch (_: Exception) {
                defaultValue
            }
        }

        override fun write(preferences: JavaPreferences, key: String, value: T) {
            preferences.putInt(key, serializer(value))
        }
    }
}
