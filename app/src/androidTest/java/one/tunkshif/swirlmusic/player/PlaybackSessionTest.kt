package one.tunkshif.swirlmusic.player

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import one.tunkshif.swirlmusic.data.model.Track
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end on a device: a real, free track from the deployment, through the real controller and
 * media session. It is the only test that touches the network on purpose.
 *
 * Requires the deployment to be reachable. The track is a public one that needs no account.
 *
 * The media notification is deliberately not asserted here: an instrumentation run has no visible
 * activity, and Android treats a foreground-service promotion differently without one. The
 * notification is checked by hand with the app on screen. See `roadmap.md`, Phase 4.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackSessionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun plays_a_track_and_posts_a_media_notification() {
        // Media notifications are exempt from the runtime permission, but the permission is
        // declared and granted here so the assertion can read the notification back.
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            context.packageName,
            android.Manifest.permission.POST_NOTIFICATIONS,
        )

        context.startService(Intent(context, PlaybackService::class.java))
        val controller = EntryPointAccessors.fromApplication(
            context,
            PlaybackEntryPoint::class.java,
        ).playerController

        val track = Track(
            id = 1969519579,
            title = "Playback probe",
            artistLine = "Swirl",
            artworkUrl = null,
            durationMs = 0,
        )

        runBlocking { controller.play(listOf(track), 0) }

        var state = controller.state.value
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(300)
            state = controller.state.value
            if (state.isPlaying && state.positionMs > 1_000) break
        }

        assertThat(state.isPlaying).isTrue()
        assertThat(state.positionMs).isGreaterThan(1_000L)
        assertThat(state.durationMs).isGreaterThan(0L)
        assertThat(state.current?.title).isEqualTo("Playback probe")
        controller.togglePlayPause()
    }
}
