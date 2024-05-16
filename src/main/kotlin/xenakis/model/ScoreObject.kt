package xenakis.model

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import xenakis.impl.ScWriter
import xenakis.impl.UDPSuperColliderClient
import xenakis.sc.ControlSpec
import xenakis.ui.format

@Serializable
sealed class ScoreObject {
    abstract var name: String
    abstract var start: Double
    abstract var duration: Double
    abstract var y: Double
    abstract var height: Double
    abstract val color: Color?
    abstract var muted: Boolean

    @Transient
    lateinit var context: Context

    abstract val controls: List<ParameterControl>

    open val associatedEnvelopes: List<EnvelopeControl> get() = controls.filterIsInstance<EnvelopeControl>()

    open fun initialize(project: XenakisProject) {}

    open fun onRemove() {}

    open fun writeStartCode(writer: ScWriter, offset: Double) {}

    open fun writeStopCode(writer: ScWriter) {}

    abstract fun clone(newName: String): ScoreObject

    open fun getSpec(parameter: String): ControlSpec =
        throw NoSuchElementException("no spec for parameter $parameter in $this")

    fun play(client: UDPSuperColliderClient) {
        client.postAsync {
            appendLine("Task{")
            writeStartCode(this, offset = 0.0)
            appendLine("${duration.format(2)}.wait;")
            writeStopCode(this)
            appendLine("}.play")
        }
    }
}