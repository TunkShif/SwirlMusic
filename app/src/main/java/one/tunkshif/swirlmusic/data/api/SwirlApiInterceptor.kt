package one.tunkshif.swirlmusic.data.api

import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import one.tunkshif.swirlmusic.data.session.SessionCache

/**
 * Adds the three things every call to the deployment needs:
 *
 * - `X-Api-Token`, the gate on our own server.
 * - `noCookie=true`, so the server never answers with `Set-Cookie`.
 * - `timestamp`, on QR login calls, because the server caches every response for two minutes.
 *
 * The NetEase session cookie travels as a header. Never in the query string: URLs are logged.
 */
class SwirlApiInterceptor @Inject constructor(
    private val config: ApiConfig,
    private val sessionCache: SessionCache,
    private val timeSource: TimeSource,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(decorate(chain.request()))

    /**
     * The whole behaviour of this interceptor, kept separate from [Interceptor.Chain] so it can be
     * tested without a network stack or a hand-written chain.
     */
    internal fun decorate(original: Request): Request {
        val url = original.url.newBuilder()
            .addQueryParameter(QUERY_NO_COOKIE, "true")
            .apply {
                if (original.url.encodedPath.startsWith(QR_LOGIN_PATH_PREFIX)) {
                    addQueryParameter(QUERY_TIMESTAMP, timeSource.nowMillis().toString())
                }
            }
            .build()

        return original.newBuilder()
            .url(url)
            .header(HEADER_TOKEN, config.token)
            .apply { sessionCache.cookie?.let { header(HEADER_COOKIE, it) } }
            .build()
    }

    companion object {
        const val HEADER_TOKEN = "X-Api-Token"
        const val HEADER_COOKIE = "Cookie"
        const val QUERY_NO_COOKIE = "noCookie"
        const val QUERY_TIMESTAMP = "timestamp"
        const val QR_LOGIN_PATH_PREFIX = "/login/qr/"
    }
}
