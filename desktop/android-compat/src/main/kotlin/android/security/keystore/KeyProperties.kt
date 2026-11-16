package android.security.keystore

/**
 * android-compat: `android.security.keystore.KeyProperties` constants. There is no Android Keystore
 * on desktop; these only exist so code that builds JCA transformation names from them compiles.
 *
 * One deliberate difference: [ENCRYPTION_PADDING_PKCS7] is `"PKCS5Padding"`, the name the JDK's
 * default provider (SunJCE) uses for the same padding (PKCS#5 and PKCS#7 padding are identical
 * for AES' 16-byte blocks; Android's providers accept `"PKCS7Padding"`, SunJCE does not). So
 * `"$KEY_ALGORITHM_AES/$BLOCK_MODE_CBC/$ENCRYPTION_PADDING_PKCS7"` works with `Cipher.getInstance`
 * on both platforms, and the ciphertext is the same.
 */
object KeyProperties {
    const val PURPOSE_ENCRYPT = 1
    const val PURPOSE_DECRYPT = 2
    const val PURPOSE_SIGN = 4
    const val PURPOSE_VERIFY = 8

    const val KEY_ALGORITHM_AES = "AES"
    const val KEY_ALGORITHM_RSA = "RSA"
    const val KEY_ALGORITHM_EC = "EC"
    const val KEY_ALGORITHM_HMAC_SHA256 = "HmacSHA256"

    const val BLOCK_MODE_ECB = "ECB"
    const val BLOCK_MODE_CBC = "CBC"
    const val BLOCK_MODE_CTR = "CTR"
    const val BLOCK_MODE_GCM = "GCM"

    const val ENCRYPTION_PADDING_NONE = "NoPadding"
    const val ENCRYPTION_PADDING_PKCS7 = "PKCS5Padding"
}
