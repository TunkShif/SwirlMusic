package one.tunkshif.swirlmusic.data.api

/**
 * Where the API lives and how it proves we may use it.
 *
 * Both values come from `local.properties` at build time, and are visible inside the APK.
 * They are a gate, not a secret.
 */
data class ApiConfig(
    val baseUrl: String,
    val token: String,
) {
    init {
        require(baseUrl.isNotBlank()) { "swirl.apiBaseUrl is missing from local.properties" }
        require(token.isNotBlank()) { "swirl.apiToken is missing from local.properties" }
    }

    fun urlOf(path: String): String = baseUrl.trimEnd('/') + path
}
