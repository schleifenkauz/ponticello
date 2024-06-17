package xenakis.model

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.getString
import xenakis.impl.getValue
import xenakis.impl.putSerializableValue
import xenakis.impl.setValue
import xenakis.sc.ControlSpec
import xenakis.ui.ScoreObjectView

class ClonedObject(private var originalRef: Reference) : ScoreObject() {
    constructor(original: ScoreObject) : this(original.createReference())

    override val mutableName: ReactiveVariable<String>
        get() = original.mutableName

    override val position: ObjectPosition = ObjectPosition()
    val original: ScoreObject get() = originalRef.get()

    override val type: String
        get() = "clone"

    override var start: Double by position::time
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

    override fun writeCode(env: ScorePlayEnv, name: String, playAt: Double) = original.writeCode(env, name, playAt)

    override fun writeStartCode(env: ScorePlayEnv, offset: Double, name: String) =
        original.writeStartCode(env, offset, name)

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