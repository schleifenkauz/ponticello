package ponticello.model.score

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.model.flow.NodePlacement
import ponticello.model.obj.ParameterDefObject
import ponticello.model.score.controls.ParameterControl
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveValue
import reaktive.value.reactiveVariable

@Serializable
@SerialName("Unresolved")
class UnresolvedScoreObject : ScoreObject() {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    override val type: String
        get() = "none"

    override val name: ReactiveValue<String> get() = reactiveVariable("<unresolved>")

    override val associatedColor: ReactiveValue<Color?>
        get() = reactiveValue(Color.gray(0.5, 0.5))

    override val affectsPlayback: Boolean
        get() = false

    override fun writeCode(
        instance: ScoreObjectInstance?,
        uniqueName: String,
        placement: NodePlacement?,
        cutoff: Decimal,
        latency: Decimal,
        extraArguments: Map<ParameterDefObject, ParameterControl>,
    ): String = ""

    override fun doClone(): ScoreObject = this

    override fun initialize(context: Context) {
        setContext(context)
    }
}