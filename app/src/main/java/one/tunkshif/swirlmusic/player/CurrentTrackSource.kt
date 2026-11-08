package one.tunkshif.swirlmusic.player

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The id of the track playing now, for row selection (0012-D52). */
interface CurrentTrackSource {
    val currentTrackId: Flow<Long?>
}

@Singleton
class PlayerCurrentTrackSource @Inject constructor(
    controller: PlayerController,
) : CurrentTrackSource {
    override val currentTrackId: Flow<Long?> =
        controller.state.map { it.current?.id }.distinctUntilChanged()
}
