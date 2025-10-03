package ponticello.model.git

import kotlinx.serialization.Serializable

@Serializable
data class DeviceCodeResponse(
    val error: String? = null,
    val error_description: String? = null,
    val device_code: String? = null,
    val user_code: String? = null,
    val verification_uri: String? = null,
    val expires_in: Int = 0,
    val interval: Int = 5
)

@Serializable
data class AccessTokenResponse(
    val access_token: String? = null,
    val token_type: String? = null,
    val scope: String? = null,
    val error: String? = null,
    val error_description: String? = null,
)

