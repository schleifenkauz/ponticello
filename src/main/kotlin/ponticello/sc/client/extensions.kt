package ponticello.sc.client

import com.illposed.osc.OSCMessage
import ponticello.impl.Logger
import ponticello.model.GlobalSettings
import reaktive.value.now
import java.util.concurrent.CompletableFuture

inline fun <reified T : Any> OSCMessage.getArgument(index: Int, name: String): T? {
    if (index !in arguments.indices) {
        Logger.warn("Index for argument '$name' out of bounds: $index", Logger.Category.SuperCollider)
        return null
    }
    val argument = arguments[index]
    if (argument !is T) {
        Logger.warn("Argument '$name' is not of type ${T::class.java.canonicalName}", Logger.Category.SuperCollider)
        return null
    }
    return argument
}

val OSCMessage.id get() = getArgument<Int>(0, "id")

fun SuperColliderClient.isServerRunning() = try {
    eval("s.serverRunning").get().toBoolean()
} catch (e: Exception) {
    Logger.error("Failed to check if server is running", e)
    false
}

fun SuperColliderClient.eval(
    code: String, description: String = "evaluating $code",
    onError: (String) -> Unit = {}, onSuccess: (String) -> Unit,
) {
    eval(code, description).whenComplete { result, error ->
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

fun SuperColliderClient.eval(code: String, description: String = "evaluating $code"): CompletableFuture<String> {
    Logger.fine("eval $code", Logger.Category.SuperCollider, detailMessage = code)
    if (context[GlobalSettings].logScCode.now) {
        println("################ EVAL #################")
        println(code)
        println("################ END #################")
    }
    return send("eval", listOf(code), description)
}

fun SuperColliderContext.run(writeCode: ScWriter.() -> Unit) {
    val command = ponticello.impl.writeCode(writeCode)
    if (command.isNotBlank()) run(command)
}

fun SuperColliderClient.eval(description: String? = null, writeCode: ScWriter.() -> Unit): CompletableFuture<String> {
    val command = ponticello.impl.writeCode(writeCode)
    if (command.isNotBlank()) return eval(command, description ?: "evaluating $command")
    return CompletableFuture.completedFuture("")
}