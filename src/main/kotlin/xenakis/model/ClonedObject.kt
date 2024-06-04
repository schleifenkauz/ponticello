package xenakis.model

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.*
import xenakis.sc.ControlSpec
import xenakis.ui.ScoreObjectView

class ClonedObject(
    name: String,
    private var originalRef: Reference,
) : ScoreObject(name) {
    constructor(name: String, original: ScoreObject) : this(name, original.createReference())

    override val position: ObjectPosition = ObjectPosition(this)
    val original: ScoreObject get() = originalRef.get()

    override val type: String
        get() = "clone"

    override var start: Double by position::start
    override var y: Double by position::y

    override var nextInChain: Reference? = null

    override fun JsonObjectBuilder.saveToJson() {
        put("original", original.name.now)
    }

    override fun initialize(context: Context) {
        super.initialize(context)
        originalRef.resolve(context)
    }

    override var duration: Double by { original::duration }
    override var height: Double by { original::height }
    override val associatedColor: ReactiveVariable<Color?> get() = original.associatedColor
    override var muted: Boolean by { original::muted }
    override val associatedControls: Map<String, ParameterControl> get() = original.associatedControls

    override fun getSpec(parameter: String): ControlSpec = original.getSpec(parameter)

    override fun writeStartCode(writer: ScWriter, offset: Double, name: String) =
        original.writeStartCode(writer, offset, this.name.now)

    override fun writeStopCode(writer: ScWriter, name: String) =
        original.writeStopCode(writer, this.name.now)

    override fun play(client: SuperColliderClient) = original.play(client)

    override fun copy(newName: String): ScoreObject = original.copy(newName)

    override fun clone(name: String): ClonedObject = original.clone(name)

    override fun addView(view: ScoreObjectView) {
        original.addView(view)
    }

    object Serializer : ScoreObject.Serializer {
        override val type: String
            get() = "clone"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val originalName: Reference = getSerializableValue("original")!!
            return ClonedObject(name, originalName)
        }
    }
}