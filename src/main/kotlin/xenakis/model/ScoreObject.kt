package xenakis.model

import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import xenakis.impl.ScWriter
import xenakis.impl.UDPSuperColliderClient
import xenakis.ui.format
import java.io.StringWriter

@Serializable
sealed class ScoreObject {
    abstract var name: String
    abstract var start: Double
    abstract var duration: Double
    abstract var y: Double
    abstract var height: Double
    abstract val color: Color?

    abstract val controls: List<ParameterControl>

    open val associatedEnvelopes: List<EnvelopeControl> get() = controls.filterIsInstance<EnvelopeControl>()

    abstract fun initialize(project: XenakisProject)

    abstract fun writeStartCode(writer: ScWriter, offset: Double)

    abstract fun writeStopCode(writer: ScWriter)

    abstract fun clone(newName: String): ScoreObject

    fun play(client: UDPSuperColliderClient) {
        val writer = StringWriter()
        with(ScWriter(writer)) {
            appendLine("(Task{")
            writeStartCode(this, offset = 0.0)
            appendLine("${duration.format(2)}.wait;")
            writeStopCode(this)
            appendLine("}.play)")
        }
        val code = writer.toString()
        client.postAsync(code)
    }
}