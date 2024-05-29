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
) : ScoreObject {
    constructor(name: String, original: ScoreObject) : this(name, original.name) {
        this.original = original
        resolved = true
    }

    override val position: ObjectPosition = ObjectPosition(this)
    lateinit var original: ScoreObject
        private set

    private var resolved = false

    override val type: String
        get() = "clone"

    override var start: Double by position::start
    override var y: Double by position::y

    override var nameOfNextInChain: String? = null
    override var nextInChain: ClonedObject? = null

    override fun JsonObjectBuilder.saveToJson() {
        put("original", original.name)
    }

    override val parent: Score
        get() = original.parent

    override fun addToScore(score: Score, context: Context) {
        super.addToScore(score, context)
        if (!resolved) {
            original = score.getObject(originalName)
            resolved = true
        }
    }

    override var duration: Double by { original::duration }
    override var height: Double by { original::height }
    override var associatedColor: Color? by { original::associatedColor }
    override var muted: Boolean by { original::muted }
    override val associatedControls: Map<String, ParameterControl> get() = original.associatedControls

    override fun getSpec(parameter: String): ControlSpec = original.getSpec(parameter)

    override fun writeStartCode(writer: ScWriter, offset: Double) = original.writeStartCode(writer, offset)

    override fun writeStopCode(writer: ScWriter) = original.writeStopCode(writer)

    override fun play(client: UDPSuperColliderClient) = original.play(client)

    override fun copy(newName: String): ScoreObject = original.copy(newName)

    override fun clone(name: String): ClonedObject = original.clone(name)

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