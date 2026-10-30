package one.tunkshif.swirlmusic.data.api

/** Time as the app sees it. Injected so tests can pin it. */
interface TimeSource {
    fun nowMillis(): Long
}

object SystemTimeSource : TimeSource {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
