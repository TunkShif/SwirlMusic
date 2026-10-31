package one.tunkshif.swirlmusic.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.EntryPointAccessors
import one.tunkshif.swirlmusic.MainActivity

/**
 * Hosts the media session for [PlayerController]'s player.
 *
 * The session is what puts controls in the notification and on the lock screen. The player itself
 * is owned by the controller, so playback survives the service being recreated.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    private val controller: PlayerController
        get() = EntryPointAccessors.fromApplication(
            applicationContext,
            PlaybackEntryPoint::class.java,
        ).playerController

    override fun onCreate() {
        super.onCreate()

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        session = MediaSession.Builder(this, controller.player)
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!controller.player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
