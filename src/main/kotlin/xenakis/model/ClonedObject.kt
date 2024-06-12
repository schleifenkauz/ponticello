package xenakis.model

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.*
import xenakis.sc.ControlSpec
import xenakis.ui.ScoreObjectView

class ClonedObject(private var originalRef: Reference) : ScoreObject() {
    constructor(original: ScoreObject) : this(original.createReference())

    override val mutableName: ReactiveVariable<String>
        get() = original.mutableName

    override val position: ObjectPosition = ObjectPosition(this)
    val original: ScoreObject get() = originalRef.get()

    override val type: String
        get() = "clone"

    override var start: Double by position::start
    override var y: Double by position::y

    override var nextInChain: Reference? = null

    override fun JsonObjectBuilder.saveToJson() {
        putSerializableValue("scoreOfOriginal", original.parent!!.scoreName.now)
    }

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        originalRef.resolve(context)
    }

    override var duration: Double by { original::duration }
    override var height: Double by { original::height }
    override val associatedColor: ReactiveVariable<Color?> get() = original.associatedColor
    override var muted: Boolean by { original::muted }
    override val associatedControls: Map<String, ParameterControl> get() = original.associatedControls

    override fun getSpec(parameter: String): ControlSpec = original.getSpec(parameter)

    override fun writeCode(writer: ScWriter, playAt: Double, name: String) = original.writeCode(writer, playAt, name)

    override fun writeStartCode(writer: ScWriter, offset: Double, name: String) =
        original.writeStartCode(writer, offset, name)

    override fun play(writer: ScWriter) = original.play(writer)

    override fun copy(newName: String): ScoreObject {
        val copy = original.copy(newName)
        copy.position.set(this.position)
        return copy
    }

    override fun clone(): ClonedObject {
        val clone = original.clone()
        clone.position.set(this.position)
        return clone
    }

    override fun addView(view: ScoreObjectView) {
        original.addView(view)
    }

    object Serializer : ScoreObject.Serializer {
        override val type: String
            get() = "clone"

        override fun JsonObject.createFromJson(name: String): ScoreObject {
            val scoreOfOriginal = getString("scoreOfOriginal")!!
            val originalRef = Reference(scoreOfOriginal, name)
            return ClonedObject(originalRef)
        }
    }
}