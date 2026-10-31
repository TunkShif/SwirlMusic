package one.tunkshif.swirlmusic.ui.login

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.auth.LoginError
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class LoginScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `the welcome copy offers the QR action`() {
        var started = false
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                LoginScreen(
                    state = LoginUiState(),
                    onStart = { started = true },
                    onCancel = {},
                    onSaveQr = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.welcome_action)).performClick()

        assertThat(started).isTrue()
    }

    @Test
    fun `the waiting state shows the code and the save action`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                LoginScreen(
                    state = LoginUiState(
                        phase = LoginPhase.Waiting,
                        qrUrl = "https://music.163.com/login?codekey=key-1",
                    ),
                    onStart = {},
                    onCancel = {},
                    onSaveQr = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.login_waiting_title)).assertExists()
        compose.onNodeWithContentDescription(text(R.string.login_qr_description)).assertExists()
        compose.onNodeWithText(text(R.string.login_save_qr)).assertExists()
    }

    @Test
    fun `the scanned state asks for confirmation`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                LoginScreen(
                    state = LoginUiState(
                        phase = LoginPhase.Scanned,
                        qrUrl = "https://music.163.com/login?codekey=key-1",
                    ),
                    onStart = {},
                    onCancel = {},
                    onSaveQr = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.login_scanned_title)).assertExists()
        compose.onNodeWithText(text(R.string.login_save_qr)).assertExists()
    }

    @Test
    fun `an expired code offers a new one`() {
        var restarted = false
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                LoginScreen(
                    state = LoginUiState(phase = LoginPhase.Expired),
                    onStart = { restarted = true },
                    onCancel = {},
                    onSaveQr = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.login_expired_title)).assertExists()
        compose.onNodeWithText(text(R.string.login_retry)).performClick()

        assertThat(restarted).isTrue()
    }

    @Test
    fun `a network failure says so`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                LoginScreen(
                    state = LoginUiState(phase = LoginPhase.Failed, error = LoginError.Network),
                    onStart = {},
                    onCancel = {},
                    onSaveQr = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.login_failed_network)).assertExists()
    }

    @Test
    fun `a server failure says so`() {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                LoginScreen(
                    state = LoginUiState(phase = LoginPhase.Failed, error = LoginError.Server),
                    onStart = {},
                    onCancel = {},
                    onSaveQr = {},
                )
            }
        }

        compose.onNodeWithText(text(R.string.login_failed_server)).assertExists()
    }

    private fun text(resId: Int): String = context.getString(resId)
}
