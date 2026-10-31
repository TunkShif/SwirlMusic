package one.tunkshif.swirlmusic.data.auth

/**
 * Polling behaviour of QR sign-in. A data class so tests can shrink the timings and run in
 * virtual time.
 */
data class LoginTuning(
    val pollIntervalMillis: Long = 2_000,
    val timeoutMillis: Long = 180_000,
    /** Consecutive network failures tolerated before the flow gives up. */
    val maxNetworkRetries: Int = 3,
)
