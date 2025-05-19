package ponticello.model.player

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.impl.zero
import ponticello.model.registry.BufferRegistry
import ponticello.sc.client.OSCListener
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject
import java.io.File
import kotlin.concurrent.thread

class CircularBufferRecorder(
    private val context: Context,
    private val client: SuperColliderClient,
) : OSCListener {
    init {
        client.addListener(this)
    }

    var bufferDuration = zero
        private set

    val isRecording get() = bufferDuration > zero

    fun startRecording(duration: Decimal) {
        client.send("startCircularBuffer", listOf(duration.toString()))
        bufferDuration = duration
    }

    fun markSegmentEnd() {
        println("/markBufferSegmentEnd")
        client.send("/markBufferSegmentEnd").join()
    }

    fun exportSegment(duration: Decimal, name: String) {
        val sampleDirectory = context[currentProject].projectDirectory.resolve("samples")
        sampleDirectory.mkdirs()
        val path = sampleDirectory.resolve("$name.wav")
        client.send("exportBufferSegment", listOf(path.absolutePath, duration.toString()))
    }

    override fun onMessage(path: String, content: String) {
        thread {
            println("Received message $path: $content")
            if (path != "/complet") return@thread
            val file = File(content)
            Thread.sleep(50)
            if (!file.isFile) {
                Logger.error("Could not find file $content", Logger.Category.Buffers)
                return@thread
            }
            context[BufferRegistry].getOrAdd(file)
            Logger.confirm("File ${file.name} recorded", Logger.Category.Buffers)
        }
    }

    companion object : PublicProperty<CircularBufferRecorder> by publicProperty("CircularBufferRecorder")
}