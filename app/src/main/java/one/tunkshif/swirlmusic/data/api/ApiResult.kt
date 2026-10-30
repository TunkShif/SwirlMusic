package one.tunkshif.swirlmusic.data.api

/**
 * The result of one API call. Every failure that the UI must distinguish has its own case;
 * nothing above the data layer sees an exception.
 */
sealed interface ApiResult<out T> {

    data class Success<T>(val value: T) : ApiResult<T>

    /** The session is gone. `code: 301` in a NetEase body, or an HTTP 401. */
    data class Unauthorized(val body: String?) : ApiResult<Nothing>

    data class HttpError(val code: Int, val body: String?) : ApiResult<Nothing>

    data class NetworkError(val cause: Throwable) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Unauthorized -> this
    is ApiResult.HttpError -> this
    is ApiResult.NetworkError -> this
}
