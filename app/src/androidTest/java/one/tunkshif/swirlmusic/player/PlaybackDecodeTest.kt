package one.tunkshif.swirlmusic.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import one.tunkshif.swirlmusic.test.R as TestR
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Decoding needs a real audio stack, so this runs on a device. It proves the player advances
 * through a bundled asset, which is the part a JVM test cannot cover.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackDecodeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun plays_a_bundled_asset_and_advances() {
        // The asset lives in the test APK, so copy it where the app's data source can read it.
        val audioFile = File(context.cacheDir, "test_tone.wav")
        instrumentation.context.resources.openRawResource(TestR.raw.test_tone).use { input ->
            audioFile.outputStream().use { output -> input.copyTo(output) }
        }

        lateinit var player: ExoPlayer

        instrumentation.runOnMainSync {
            player = ExoPlayer.Builder(context).build()
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(audioFile)))
            player.prepare()
            player.play()
        }

        var position = 0L
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(250)
            instrumentation.runOnMainSync { position = player.currentPosition }
            if (position > 500) break
        }

        var isPlaying = false
        instrumentation.runOnMainSync {
            isPlaying = player.isPlaying
            player.release()
        }

        assertThat(position).isGreaterThan(500L)
        assertThat(isPlaying).isTrue()
    }
}
