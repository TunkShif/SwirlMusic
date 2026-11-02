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
