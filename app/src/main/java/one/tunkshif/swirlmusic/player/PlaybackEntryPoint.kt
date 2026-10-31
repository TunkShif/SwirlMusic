package one.tunkshif.swirlmusic.player

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt rejects `@AndroidEntryPoint` on a class whose immediate parent is not a framework type, and
 * `MediaSessionService` sits between Android's `Service` and ours. An entry point gives the service
 * the controller without annotations on the service class.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaybackEntryPoint {
    val playerController: PlayerController
}
