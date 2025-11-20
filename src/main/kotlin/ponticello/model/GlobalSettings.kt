package ponticello.model

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.withPrecision
import ponticello.model.instr.ParameterDefObject
import ponticello.ui.registry.ParameterDefList
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class GlobalSettings(
    val defaultParametersDefs: ParameterDefList = ParameterDefList(),
    val scLangLatency: ReactiveVariable<Decimal> = reactiveVariable(0.1.withPrecision(2)),
    val serverLatency: ReactiveVariable<Decimal> = reactiveVariable(0.1.withPrecision(2)),
    val extraLatency: ReactiveVariable<Decimal> = reactiveVariable(0.05.withPrecision(2)),
    val periodicGarbageCollection: ReactiveVariable<Boolean> = reactiveVariable(false),
    val garbageCollectionPeriod: ReactiveVariable<Decimal> = reactiveVariable(60.0.withPrecision(0)),
    val logScCode: ReactiveVariable<Boolean> = reactiveVariable(false),
    val knobSensitivity: ReactiveVariable<Decimal> = reactiveVariable(3.0.withPrecision(1)),
) {
    fun getDefaultControlSpec(name: String) = defaultParametersDefs.find { p -> p.name.now == name }?.spec?.now

    fun initialize(context: Context) {
        defaultParametersDefs.initialize(context)
    }

    companion object : PublicProperty<GlobalSettings> by publicProperty("SETTINGS") {
        fun createDefault(): GlobalSettings = GlobalSettings(
            defaultParametersDefs = ParameterDefList(ParameterDefObject.defaults.toMutableList())
        )
    }
}
