package one.tunkshif.swirlmusic.data.api.dto

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Contract tests. Each fixture is a real (or documented) NetEase payload; if a parser test fails,
 * the upstream shape changed and the app needs to follow.
 */
class AccountResponseParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `signed out login status carries no account or profile`() {
        val response = json.decodeFromString<LoginStatusResponse>(fixture("login_status_signed_out.json"))

        assertThat(response.data.code).isEqualTo(200)
        assertThat(response.data.account).isNull()
        assertThat(response.data.profile).isNull()
    }

    @Test
    fun `signed in account exposes the profile fields the app uses`() {
        val response = json.decodeFromString<UserAccountResponse>(fixture("user_account_signed_in.json"))

        assertThat(response.code).isEqualTo(200)
        assertThat(response.account?.id).isEqualTo(90000001L)
        assertThat(response.account?.vipType).isEqualTo(11)
        assertThat(response.profile?.userId).isEqualTo(90000001L)
        assertThat(response.profile?.nickname).isEqualTo("swirl-test")
        assertThat(response.profile?.avatarUrl).isEqualTo("https://example.invalid/avatar.jpg")
        assertThat(response.profile?.vipType).isEqualTo(11)
    }

    @Test
    fun `unknown fields are ignored rather than fatal`() {
        val payload = """
            {"code":200,"profile":{"userId":1,"nickname":"n","newFieldFromNetEase":{"nested":true}}}
        """.trimIndent()

        val response = json.decodeFromString<UserAccountResponse>(payload)

        assertThat(response.profile?.nickname).isEqualTo("n")
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource("fixtures/ncm/$name")) {
            "missing fixture $name"
        }.readText()
}
