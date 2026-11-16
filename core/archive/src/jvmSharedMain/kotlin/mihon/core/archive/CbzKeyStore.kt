package mihon.core.archive

import javax.crypto.SecretKey

/**
 * KMK: storage of the AES keys [CbzCrypto] encrypts the CBZ and database passwords with.
 *
 * - Android (`src/androidMain`): Android Keystore (the key never leaves the keystore).
 * - Desktop (`src/desktopMain`): a random key in a file under the app data dir with owner-only
 *   permissions where the OS supports them. Weaker than the Android Keystore: anyone who can read
 *   the user's files can read the key.
 */
internal expect object CbzKeyStore {

    /** The key stored under [alias], generated first if there is none. */
    fun getKey(alias: String): SecretKey

    /** Generates and stores a new key under [alias] (replacing any existing one). */
    fun generateKey(alias: String): SecretKey

    /** Deletes the key stored under [alias], if any. */
    fun deleteKey(alias: String)
}
