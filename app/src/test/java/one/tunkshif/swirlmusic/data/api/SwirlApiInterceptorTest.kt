package one.tunkshif.swirlmusic.data.api

import com.google.common.truth.Truth.assertThat
import okhttp3.Request
import one.tunkshif.swirlmusic.data.session.SessionCache
import org.junit.Test

class SwirlApiInterceptorTest {

    private val config = ApiConfig(baseUrl = "https://api.example", token = "token-1")
    private val cache = SessionCache()
    private val timeSource = object : TimeSource {
        override fun nowMillis(): Long = 1_700_000_000_000
    }
    private val interceptor = SwirlApiInterceptor(config, cache, timeSource)

    @Test
    fun `adds the api token and disables Set-Cookie`() {
        val request = decorate("/login/status")

        assertThat(request.header(SwirlApiInterceptor.HEADER_TOKEN)).isEqualTo("token-1")
        assertThat(request.url.queryParameter(SwirlApiInterceptor.QUERY_NO_COOKIE)).isEqualTo("true")
    }

    @Test
    fun `omits the cookie header when signed out`() {
        assertThat(decorate("/login/status").header(SwirlApiInterceptor.HEADER_COOKIE)).isNull()
    }

    @Test
    fun `sends the session cookie as a header, never in the url`() {
        cache.update("MUSIC_U=abc; __csrf=def")

        val request = decorate("/user/account")

        assertThat(request.header(SwirlApiInterceptor.HEADER_COOKIE))
            .isEqualTo("MUSIC_U=abc; __csrf=def")
        assertThat(request.url.toString()).doesNotContain("MUSIC_U")
    }

    @Test
    fun `adds a timestamp to qr login calls only`() {
        assertThat(decorate("/login/qr/key").url.queryParameter(SwirlApiInterceptor.QUERY_TIMESTAMP))
            .isEqualTo("1700000000000")
        assertThat(decorate("/login/status").url.queryParameter(SwirlApiInterceptor.QUERY_TIMESTAMP))
            .isNull()
    }

    private fun decorate(path: String): Request =
        interceptor.decorate(Request.Builder().url(config.urlOf(path)).build())
}
