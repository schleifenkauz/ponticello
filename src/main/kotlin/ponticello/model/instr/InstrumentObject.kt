package ponticello.model.instr

import fxutils.drag.TypedDataFormat
import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import ponticello.model.obj.BusReference
import ponticello.model.obj.SuperColliderObject
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.model.score.ScoreObjectGroup
import ponticello.model.score.controls.BusControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.server.BusRegistry
import ponticello.sc.BusControlSpec
import ponticello.sc.ControlSpec
import ponticello.sc.defaultControl
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
sealed interface InstrumentObject : SuperColliderObject {
    val color: ReactiveValue<Color>

    val instrumentType: String

    val parameters: List<ParameterDefObject>

    override val registry: InstrumentRegistry?
        get() = context[InstrumentRegistry]

    fun allParameters(): List<ParameterDefObject> = parameters

    fun getParameter(name: String): ParameterDefObject? = allParameters().find { p -> p.name.now == name }

    fun getSpec(name: String): ReactiveVariable<ControlSpec>? = getParameter(name)?.spec

    fun setSpec(parameterName: String, spec: ControlSpec) {
        val parameter = getParameter(parameterName) ?: error("Parameter $parameterName not found in $this")
        parameter.spec.now = spec
    }

    fun hasParameter(name: String): Boolean = allParameters().any { it.name.now == name }

    fun defaultControls(
        context: Context, defaultBus: BusReference?,
    ): MutableMap<String, ParameterControl> = allParameters().associateTo(mutableMapOf()) { p ->
        val ctrl =
            if (p.spec.now is BusControlSpec && defaultBus != null) BusControl(reactiveVariable(defaultBus))
            else p.spec.now.defaultControl()
        p.name.now to ctrl
    }

    fun getDefaultControls(associatedObject: ScoreObjectGroup?): MutableMap<String, ParameterControl> {
        val defaultBus = associatedObject?.defaultBusRef?.now ?: context[BusRegistry].getDefault().reference()
        return defaultControls(context, defaultBus)
    }

    fun instrumentReference(): InstrumentReference = InstrumentReference.UserDefined(this.reference())

    companion object {
        val DATA_FORMAT = TypedDataFormat<ObjectReference<InstrumentObject>>("ponticello:instrument")
    }
}