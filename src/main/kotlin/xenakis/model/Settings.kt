package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import kotlinx.serialization.Serializable
import reaktive.list.MutableReactiveList
import reaktive.list.reactiveList
import reaktive.list.toReactiveList
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class Settings(
    val defaultParametersDefs: MutableReactiveList<ParameterDefObject> = reactiveList(),
    val scLangLatency: ReactiveVariable<Double> = reactiveVariable(0.1),
    val serverLatency: ReactiveVariable<Double> = reactiveVariable(0.1)
) {
    fun getDefaultControlSpec(name: String) = defaultParametersDefs.now.find { p -> p.name.now == name }?.spec?.now

    companion object : PublicProperty<Settings> by publicProperty("SETTINGS") {
        fun createDefault(): Settings = Settings(
            defaultParametersDefs = ParameterDefObject.defaults.toReactiveList(),
            scLangLatency = reactiveVariable(0.1), serverLatency = reactiveVariable(0.1)
        )
    }
}
