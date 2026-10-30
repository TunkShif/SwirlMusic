package one.tunkshif.swirlmusic.data.session

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The current cookie, readable without suspending, so the HTTP interceptor can use it.
 * [SessionRepository] is the only writer.
 */
@Singleton
class SessionCache @Inject constructor() {

    @Volatile
    var cookie: String? = null
        private set

    fun update(cookie: String?) {
        this.cookie = cookie
    }
}
