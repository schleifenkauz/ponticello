package xenakis.sc.client

import xenakis.impl.Logger

fun SuperColliderClient.isServerRunning() = try {
    eval("s.serverRunning").get().toBoolean()
} catch (e: Exception) {
    Logger.error("Failed to check if server is running", e)
    false
}