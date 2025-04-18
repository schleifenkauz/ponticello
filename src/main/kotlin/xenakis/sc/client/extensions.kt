package xenakis.sc.client

import xenakis.impl.Logger

fun SuperColliderClient.isServerRunning() = try {
    eval("s.serverRunning").get().toBoolean()
} catch (e: Exception) {
    Logger.error("Failed to check if server is running", e)
    false
}

fun SuperColliderClient.eval(code: String, onError: (String) -> Unit = {}, onSuccess: (String) -> Unit) {
    eval(code).whenComplete { result, error ->
        if (error != null) {
            when (error) {
                is SuperColliderException -> onError(error.errorMessage)
                else -> {
                    onError(error.message ?: "Unknown error")
                    Logger.error("Unexpected exception evaluating '$code'", error)
                }
            }
            onError(error.message ?: "Unknown error")
        } else {
            onSuccess(result)
        }
    }
}