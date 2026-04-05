package ponticello.model.score.controls

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.ColorSerializer
import ponticello.model.instr.ParameterizedObject
import ponticello.model.score.Envelope
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Warp
import ponticello.ui.score.EnvelopeView
import reaktive.event.unitEvent
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable

@Serializable
@SerialName("Envelope")
class EnvelopeControl(
    val points: Envelope,
    val displayColor: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color> = reactiveVariable(Color.BLACK),
    val display: ReactiveVariable<Boolean> = reactiveVariable(true),
) : ParameterControl(), EnvelopeView {
    @Transient
    private val update = unitEvent()

    val updated get() = update.stream

    override fun initialize(context: Context, namedControl: NamedParameterControl) {
        super.initialize(context, namedControl)
        points.initialize(context)
        points.addListener(this)
    }

    override fun copy(): ParameterControl =
        EnvelopeControl(points = points.copy(), displayColor, display)

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean = spec is NumericalControlSpec

    override fun writeCode(parameter: String, spec: ControlSpec?, obj: ParameterizedObject): String {
        val warp = if (spec is NumericalControlSpec) spec.warp else Warp.Linear
        return "EnvelopeControl('$parameter', ${points.code(warp)})"
    }

    override fun editedEnvelope() {
        update.fire()
    }
}