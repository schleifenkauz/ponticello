package ponticello.model.git

import com.github.javakeyring.Keyring
import ponticello.impl.Logger

object GitHubAuthentication {
    private val keyring = Keyring.create()

    private const val SERVICE = "GitHub"
    private const val ACCOUNT = "PonticelloApp"

    private const val CLIENT_ID = "Iv23li3VBw0AzYeirDz3"

    suspend fun getToken(showUserCode: (code: String, verificationUri: String) -> Unit): String? {
        val stored = try {
            keyring.getPassword(SERVICE, ACCOUNT)
        } catch (e: Exception) {
            null
        }
        if (stored != null) return stored
        val deviceFlow = GitHubDeviceFlow(CLIENT_ID)
        val response = deviceFlow.requestDeviceCode()
        if (response.error != null) {
            Logger.error("Error getting GitHub token: ${response.error_description}")
            return null
        }
        val verificationUri = response.verification_uri ?: return null
        val userCode = response.user_code ?: return null
        showUserCode(userCode, verificationUri)
        println("Waiting for authorization...")
        val tokenResponse = deviceFlow.pollForAccessToken(response)
        if (tokenResponse.access_token != null) {
            Logger.confirm("Storing GitHub token in keyring.", Logger.Category.VersionControl)
            keyring.setPassword(SERVICE, ACCOUNT, tokenResponse.access_token)
            return tokenResponse.access_token
        }
        return null
    }
}