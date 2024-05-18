package xenakis.model

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import xenakis.impl.*
import xenakis.sc.ControlSpec
import xenakis.ui.ScoreObjectView

class ClonedObject(
    override var name: String,
    private val originalName: String,
    override val position: ObjectPosition = ObjectPosition()
) : ScoreObject {
    constructor(name: String, original: ScoreObject, position: ObjectPosition) : this(name, original.name, position) {
        this.original = original
    }

    lateinit var original: ScoreObject

    override val type: String
        get() = "clone"

    override var start: Double by position::start
    override var y: Double by position::y

    override fun JsonObjectBuilder.saveToJson() {
        put("original", original.name)
    }

    override val container: ScoreObjectContainer
        get() = original.container

    override fun addToContainer(container: ScoreObjectContainer, context: Context) {
        original = container.getObject(originalName)
    }

    override var duration: Double by { original::duration }
    override var height: Double by { original::height }
    override var associatedColor: Color? by { original::associatedColor }
    override var muted: Boolean by { original::muted }
    override var controls: List<ParameterControl> by { original::controls }
    override val associatedEnvelopes: List<EnvelopeControl> get() = original.associatedEnvelopes

    override fun getSpec(parameter: String): ControlSpec = original.getSpec(parameter)

    override fun writeStartCode(writer: ScWriter, offset: Double) = original.writeStartCode(writer, offset)

    override fun writeStopCode(writer: ScWriter) = original.writeStopCode(writer)

    override fun play(client: UDPSuperColliderClient) = original.play(client)

    override fun copy(newName: String): ScoreObject = ClonedObject(newName, original, position.copy())

    override fun clone(name: String, position: ObjectPosition): ScoreObject = ClonedObject(name, original, position)

    override fun addView(view: ScoreObjectView) {
        original.addView(view)
    }

    object Serializer : ScoreObject.Serializer {
        override val type: String
            get() = "clone"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val originalName = getString("original")!!
            return ClonedObject(name, originalName)
        }
    }
}