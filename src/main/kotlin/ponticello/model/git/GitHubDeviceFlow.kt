package ponticello.model.git

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import ponticello.impl.Logger
import java.time.Instant

class GitHubDeviceFlow(private val clientId: String, private val scope: String = "repo") {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }

    suspend fun requestDeviceCode(): DeviceCodeResponse {
        return client.post("https://github.com/login/device/code") {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(listOf("client_id" to clientId, "scope" to scope).formUrlEncode())
        }.body()
    }

    suspend fun pollForAccessToken(deviceResp: DeviceCodeResponse): AccessTokenResponse {
        val endBy = Instant.now().epochSecond + deviceResp.expires_in
        var interval = deviceResp.interval

        while (Instant.now().epochSecond < endBy) {
            println("Polling for access token...")
            val response: AccessTokenResponse = client.post("https://github.com/login/oauth/access_token") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    listOf(
                        "client_id" to clientId,
                        "device_code" to deviceResp.device_code,
                        "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"
                    ).formUrlEncode()
                )
                header("Accept", "application/json")
            }.body()

            if (response.access_token != null) return response

            when (response.error) {
                "authorization_pending" -> {
                    println("Authorization pending. Waiting for $interval seconds...")
                }

                "slow_down" -> {
                    interval += 5
                    println("Slowing down. Waiting for $interval seconds...")
                }

                "expired_token", "access_denied" -> {
                    break
                }

                else -> {
                    Logger.error("Unknown error: ${response.error}. ${response.error_description}")
                    return response
                }
            }

            kotlinx.coroutines.delay(interval * 1000L)
        }
        return AccessTokenResponse(error = "expired_token", error_description = "Code expired")
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}