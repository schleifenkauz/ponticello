package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.impl.withPrecision
import xenakis.model.obj.ParameterDefObject
import xenakis.ui.registry.ParameterDefList

@Serializable
class Settings(
    val defaultParametersDefs: ParameterDefList = ParameterDefList(),
    val scLangLatency: ReactiveVariable<Decimal> = reactiveVariable(0.1.withPrecision(2)),
    val serverLatency: ReactiveVariable<Decimal> = reactiveVariable(0.1.withPrecision(2)),
    val garbageCollectionPeriod: ReactiveVariable<Decimal> = reactiveVariable(60.0.withPrecision(0)),
    val knobSensitivity: ReactiveVariable<Decimal> = reactiveVariable(3.0.withPrecision(1)),
) {
    val lookAhead get() = scLangLatency.now + serverLatency.now

    fun getDefaultControlSpec(name: String) = defaultParametersDefs.find { p -> p.name.now == name }?.spec?.now

    companion object : PublicProperty<Settings> by publicProperty("SETTINGS") {
        fun createDefault(): Settings = Settings(
            defaultParametersDefs = ParameterDefList(ParameterDefObject.defaults.toMutableList())
        )
    }
}
