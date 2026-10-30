package one.tunkshif.swirlmusic.data.session

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes the encrypted session blob.
 *
 * It knows nothing about Android: storage and encryption are injected, so this class is
 * testable on a plain JVM. The Android Keystore cannot run in a JVM test, and it never has to.
 */
@Singleton
class SessionStore @Inject constructor(
    private val storage: SessionStorage,
    private val cipher: SessionCipher,
) {

    suspend fun load(): Session? {
        val stored = storage.read() ?: return null
        // A value written under a different key, or a half-written file, must not crash the app.
        val cookie = runCatching { cipher.decrypt(stored) }.getOrNull() ?: return null
        return cookie.takeIf { it.isNotBlank() }?.let(::Session)
    }

    suspend fun save(session: Session) {
        storage.write(cipher.encrypt(session.cookie))
    }

    suspend fun clear() {
        storage.write(null)
    }
}

/** Where the encrypted blob lives. */
interface SessionStorage {
    suspend fun read(): String?
    suspend fun write(value: String?)
}

/** How the cookie is protected at rest. */
interface SessionCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(ciphertext: String): String
}
