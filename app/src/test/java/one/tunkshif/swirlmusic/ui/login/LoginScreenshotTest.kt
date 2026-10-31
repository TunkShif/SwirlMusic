package one.tunkshif.swirlmusic.ui.login

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import one.tunkshif.swirlmusic.data.auth.LoginError
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class LoginScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun welcome_light() = capture("welcome-light", LoginUiState(), dark = false)

    @Test
    fun welcome_dark() = capture("welcome-dark", LoginUiState(), dark = true)

    @Test
    fun waiting_light() = capture(
        "waiting-light",
        LoginUiState(phase = LoginPhase.Waiting, qrUrl = QR_URL),
        dark = false,
    )

    @Test
    fun scanned_light() = capture(
        "scanned-light",
        LoginUiState(phase = LoginPhase.Scanned, qrUrl = QR_URL),
        dark = false,
    )

    @Test
    fun expired_light() = capture(
        "expired-light",
        LoginUiState(phase = LoginPhase.Expired),
        dark = false,
    )

    @Test
    fun failed_light() = capture(
        "failed-light",
        LoginUiState(phase = LoginPhase.Failed, error = LoginError.Network),
        dark = false,
    )

    private fun capture(name: String, state: LoginUiState, dark: Boolean) {
        compose.setContent {
            SwirlMusicTheme(darkTheme = dark, dynamicColor = false) {
                LoginScreen(
                    state = state,
                    onStart = {},
                    onCancel = {},
                    onSaveQr = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("src/test/screenshots/login/$name.png")
    }

    private companion object {
        const val QR_URL = "https://music.163.com/login?codekey=d0415c66-3dfc-4595-885c-c047f6cdc4d9"
    }
}
