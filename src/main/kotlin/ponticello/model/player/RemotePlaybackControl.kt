package ponticello.model.player

import javafx.application.Platform
import kotlinx.serialization.json.*
import ponticello.impl.Logger
import ponticello.impl.json
import reaktive.value.ReactiveBoolean
import reaktive.value.reactiveVariable
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletionStage
import kotlin.concurrent.thread

class RemotePlaybackControl(private val listener: Listener) : WebSocket.Listener {
    private val client = HttpClient.newHttpClient()
    private var socket: WebSocket? = null
    private val connected = reactiveVariable(false)

    val isConnected: ReactiveBoolean get() = connected

    fun connect(url: String) {
        thread {
            client.newWebSocketBuilder().buildAsync(URI.create(url), this)
        }
    }

    fun sendBeat(measureNumber: Int, currentBeat: Int, totalBeats: Int) {
        sendMessage {
            put("type", "beat")
            put("measureNumber", measureNumber)
            put("currentBeat", currentBeat)
            put("beatsPerBar", totalBeats)
        }
    }

    private fun sendMessage(builder: JsonObjectBuilder.() -> Unit) {
        val ws = socket ?: return
        ws.sendText(buildJsonObject(builder).toString(), true)
    }

    override fun onOpen(webSocket: WebSocket) {
        socket = webSocket
        webSocket.request(1)
        sendMessage { put("type", "i-am-ponticello") }
        Platform.runLater {
            connected.set(true)
        }
    }

    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
        try {
            webSocket.request(1)
            val json = json.parseToJsonElement(data.toString())
            if (json !is JsonObject) {
                System.err.println("Received invalid JSON data: $data")
                return null
            }
            when (val command = json["type"]!!.jsonPrimitive.content) {
                "start" -> listener.start()
                "stop" -> listener.stop()
                "forward" -> listener.forward()
                "backward" -> listener.backward()
                "volume" -> listener.setVolume(json["value"]!!.jsonPrimitive.double)
                else -> System.err.println("Unknown command '$command'")
            }
        } catch (e: Exception) {
            Logger.error("Error receiving remote playback control message", e, Logger.Category.Playback)
        }
        return null
    }

    override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
        socket = null
        Platform.runLater {
            connected.set(false)
        }
        return null
    }

    override fun onError(webSocket: WebSocket, error: Throwable?) {
        System.err.println("Error with socket connection!")
        error?.printStackTrace()
        socket = null
        Platform.runLater {
            connected.set(false)
        }
    }

    fun disconnect() {
        val ws = socket ?: return
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "user disconnected")
    }

    fun notifyStarted() {
        sendMessage { put("type", "started") }
    }

    fun notifyStopped() {
        sendMessage { put("type", "stopped") }
    }

    fun notifyPlaying() {
        sendMessage { put("type", "playing") }
    }

    interface Listener {
        fun forward()
        fun backward()
        fun start(): Boolean
        fun stop()
        fun setVolume(value: Double)
    }
}