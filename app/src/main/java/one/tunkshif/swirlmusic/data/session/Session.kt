package one.tunkshif.swirlmusic.data.session

/**
 * The NetEase session. One opaque cookie string: `MUSIC_U=…; __csrf=…`.
 *
 * The value is a credential. It is stored encrypted and never logged.
 */
data class Session(val cookie: String)
