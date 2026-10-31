package one.tunkshif.swirlmusic.ui.profile

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import one.tunkshif.swirlmusic.R
import one.tunkshif.swirlmusic.data.model.UserProfile
import one.tunkshif.swirlmusic.ui.theme.SwirlMusicTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xhdpi")
class ProfileScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = RuntimeEnvironment.getApplication()
    private val profile = UserProfile(1, "tunkshif", null, vipType = 0)

    private fun setContent(onSignOut: () -> Unit) {
        compose.setContent {
            SwirlMusicTheme(dynamicColor = false) {
                ProfileScreen(profile = profile, onSignOut = onSignOut)
            }
        }
    }

    @Test
    fun `tapping sign out opens the confirmation dialog`() {
        setContent {}
        compose.onNodeWithText(text(R.string.profile_sign_out)).performClick()
        compose.onNodeWithText(text(R.string.profile_sign_out_confirm_title)).assertExists()
        compose.onNodeWithText(text(R.string.profile_sign_out_confirm_body)).assertExists()
    }

    @Test
    fun `dismissing the dialog does not sign out`() {
        var signedOut = false
        setContent { signedOut = true }
        compose.onNodeWithText(text(R.string.profile_sign_out)).performClick()
        compose.onNodeWithText(text(R.string.action_cancel)).performClick()
        assertThat(signedOut).isFalse()
    }

    @Test
    fun `confirming the dialog signs out`() {
        var signedOut = false
        setContent { signedOut = true }
        compose.onNodeWithText(text(R.string.profile_sign_out)).performClick()
        // The dialog's confirm button carries the same label; pick it inside the dialog.
        compose.onAllNodesWithText(text(R.string.profile_sign_out))[1].performClick()
        assertThat(signedOut).isTrue()
    }

    private fun text(resId: Int): String = context.getString(resId)
}
