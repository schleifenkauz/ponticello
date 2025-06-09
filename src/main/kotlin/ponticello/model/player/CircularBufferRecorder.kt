package ponticello.model.player

import bundles.PublicProperty
import bundles.publicProperty
import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import hextant.context.Context
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.impl.zero
import ponticello.model.obj.project
import ponticello.model.registry.BufferRegistry
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.getArgument
import java.io.File
import kotlin.concurrent.thread

class CircularBufferRecorder(
    private val context: Context,
    private val client: SuperColliderClient,
) : OSCMessageListener {
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
        val sampleDirectory = context.project.projectDirectory.resolve("samples")
        sampleDirectory.mkdirs()
        val path = sampleDirectory.resolve("$name.wav")
        client.send("exportBufferSegment", listOf(path.absolutePath, duration.toString()))
    }

    override fun acceptMessage(event: OSCMessageEvent) {
        val address = event.message.address
        thread {
            if (address != "/complete") return@thread
            val path = event.message.getArgument<String>(1, "path") ?: return@thread
            val file = File(path)
            Thread.sleep(50)
            if (!file.isFile) {
                Logger.error("Could not find file $path", Logger.Category.Buffers)
                return@thread
            }
            context[BufferRegistry].getOrAdd(file)
            Logger.confirm("File ${file.name} recorded", Logger.Category.Buffers)
        }
    }

    companion object : PublicProperty<CircularBufferRecorder> by publicProperty("CircularBufferRecorder")
}