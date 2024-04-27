package xenakis.model

import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import xenakis.impl.ScWriter
import xenakis.impl.UDPSuperColliderClient
import java.io.StringWriter

@Serializable
sealed class ScoreObject {
    abstract var name: String
    abstract var start: Double
    abstract var duration: Double
    abstract var y: Double
    abstract var height: Double
    abstract val color: Color

    abstract val controls: List<ParameterControl>

    open val associatedEnvelopes: List<EnvelopeControl> get() = controls.filterIsInstance<EnvelopeControl>()

    abstract fun initialize(project: XenakisProject)

    protected abstract fun ScWriter.writeStartCode(offset: Double)

    protected abstract fun writeStopCode(writer: ScWriter)

    fun start(writer: ScWriter, startTime: Double) {
        if (startTime > start + duration) return
        val offset = (startTime - start).coerceAtLeast(0.0)
        val delay = (start - startTime).coerceAtLeast(0.0)
        writer.appendBlock("s.makeBundle($delay)") {
            writeStartCode(offset)
        }
    }

    fun stop(writer: ScWriter, startTime: Double) {
        if (startTime > start + duration) return
        val delay = (start + duration - startTime).coerceAtLeast(0.0)
        writer.appendBlock("s.makeBundle($delay)") {
            writeStopCode(writer)
        }
    }

    abstract fun clone(newName: String): ScoreObject

    fun code(): String {
        val writer = StringWriter()
        ScWriter(writer).appendGroup {
            start(this, start)
            appendLine(";")
            stop(this, start)
            appendLine(";")
        }
        return writer.toString()
    }

    fun play(client: UDPSuperColliderClient) {
        client.post(code())
    }
}