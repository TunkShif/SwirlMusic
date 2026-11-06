package one.tunkshif.swirlmusic.ui.playing

import one.tunkshif.swirlmusic.player.PlaybackError

/** Fixed states for goldens and behaviour tests. */
internal val PlayingStatePlaying = NowPlayingUiState(
    track = NowPlayingTrack(
        title = "Nightcall",
        artistLine = "Kavinsky",
        albumName = "OutRun",
        albumId = 601,
        artworkUrl = "https://p1.example.invalid/cover.jpg?param=1080y1080",
        placeholderArtworkUrl = "https://p1.example.invalid/cover.jpg?param=300y300",
    ),
    isPlaying = true,
    positionMs = 42_000,
    durationMs = 258_000,
    hasNext = true,
)

internal val PlayingStatePaused = PlayingStatePlaying.copy(isPlaying = false)

internal val PlayingStateLoading = PlayingStatePlaying.copy(isLoading = true, isPlaying = false)

internal val PlayingStateTrialError = PlayingStatePlaying.copy(
    track = PlayingStatePlaying.track!!.copy(
        title = "A track title so long it has to marquee across the whole row",
        albumName = "An album name that keeps going and going",
    ),
    isTrial = true,
    error = PlaybackError.Network,
)

internal val PlayingStateEmpty = NowPlayingUiState()

internal val QueueFixture = QueueUiState(
    items = (0L until 5).map { uid ->
        QueueItemUi(uid, "Song ${'A' + uid.toInt()}", "Artist ${'A' + uid.toInt()}", null)
    },
    currentUid = 2L,
    canReorder = true,
)

internal val LyricsSynced = LyricsUiState.Content(
    lyrics = one.tunkshif.swirlmusic.data.model.Lyrics.Synced(
        lines = (0 until 12).map { i ->
            one.tunkshif.swirlmusic.data.model.LyricLine(
                startMs = i * 4_000L,
                endMs = (i + 1) * 4_000L,
                text = "Invented lyric line " + ('A' + i),
                translation = "translated line " + ('A' + i),
            )
        },
        credits = listOf("words: synth poet"),
    ),
    extra = one.tunkshif.swirlmusic.data.local.LyricsExtra.Translation,
    extraChoices = listOf(
        one.tunkshif.swirlmusic.data.local.LyricsExtra.None,
        one.tunkshif.swirlmusic.data.local.LyricsExtra.Translation,
    ),
)

internal val LyricsRomanized = LyricsUiState.Content(
    lyrics = (LyricsSynced.lyrics as one.tunkshif.swirlmusic.data.model.Lyrics.Synced).let { s ->
        s.copy(lines = s.lines.map { it.copy(translation = null, romanization = "roma " + it.text) })
    },
    extra = one.tunkshif.swirlmusic.data.local.LyricsExtra.Romanization,
    extraChoices = listOf(
        one.tunkshif.swirlmusic.data.local.LyricsExtra.None,
        one.tunkshif.swirlmusic.data.local.LyricsExtra.Romanization,
    ),
)

internal val LyricsWordTimed = LyricsUiState.Content(
    lyrics = one.tunkshif.swirlmusic.data.model.Lyrics.Synced(
        lines = listOf(
            one.tunkshif.swirlmusic.data.model.LyricLine(
                startMs = 0, endMs = 30_000, text = "invented words fade in",
            ),
            one.tunkshif.swirlmusic.data.model.LyricLine(
                startMs = 30_000, endMs = Long.MAX_VALUE, text = "made up karaoke line",
                words = listOf(
                    one.tunkshif.swirlmusic.data.model.LyricWord(30_000, 4_000, "made"),
                    one.tunkshif.swirlmusic.data.model.LyricWord(34_000, 4_000, " up"),
                    one.tunkshif.swirlmusic.data.model.LyricWord(38_000, 4_000, " karaoke"),
                    one.tunkshif.swirlmusic.data.model.LyricWord(42_000, 8_000, " line"),
                ),
            ),
        ),
        credits = emptyList(),
    ),
    extra = one.tunkshif.swirlmusic.data.local.LyricsExtra.None,
    extraChoices = listOf(one.tunkshif.swirlmusic.data.local.LyricsExtra.None),
)

internal val LyricsInstrumental = LyricsUiState.Content(
    lyrics = one.tunkshif.swirlmusic.data.model.Lyrics.Instrumental(
        credits = listOf("composer: synth keys"),
    ),
    extra = one.tunkshif.swirlmusic.data.local.LyricsExtra.None,
    extraChoices = listOf(one.tunkshif.swirlmusic.data.local.LyricsExtra.None),
)
