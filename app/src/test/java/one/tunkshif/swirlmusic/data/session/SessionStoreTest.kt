package one.tunkshif.swirlmusic.data.session

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Runs on a plain JVM: [SessionStore] takes its storage and cipher as interfaces, so the Android
 * Keystore is not needed here.
 */
class SessionStoreTest {

    private val storage = FakeStorage()
    private val store = SessionStore(storage, FakeCipher())

    @Test
    fun `load returns null when nothing is stored`() = runTest {
        assertThat(store.load()).isNull()
    }

    @Test
    fun `saved cookie survives a round trip`() = runTest {
        store.save(Session("MUSIC_U=abc; __csrf=def"))

        assertThat(store.load()?.cookie).isEqualTo("MUSIC_U=abc; __csrf=def")
    }

    @Test
    fun `the stored blob is encrypted, not the raw cookie`() = runTest {
        store.save(Session("MUSIC_U=abc"))

        assertThat(storage.value).isEqualTo("enc:MUSIC_U=abc")
    }

    @Test
    fun `a blob that cannot be decrypted loads as null instead of throwing`() = runTest {
        storage.value = "garbage written by an older build"

        assertThat(store.load()).isNull()
    }

    @Test
    fun `clear removes the blob`() = runTest {
        store.save(Session("MUSIC_U=abc"))
        store.clear()

        assertThat(storage.value).isNull()
        assertThat(store.load()).isNull()
    }

    private class FakeStorage(var value: String? = null) : SessionStorage {
        override suspend fun read(): String? = value
        override suspend fun write(value: String?) {
            this.value = value
        }
    }

    private class FakeCipher : SessionCipher {
        override fun encrypt(plaintext: String): String = "enc:$plaintext"
        override fun decrypt(ciphertext: String): String {
            require(ciphertext.startsWith("enc:")) { "not a ciphertext" }
            return ciphertext.removePrefix("enc:")
        }
    }
}
