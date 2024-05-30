package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import kotlinx.serialization.Serializable
import reaktive.list.MutableReactiveList
import reaktive.list.reactiveList
import reaktive.list.toReactiveList
import reaktive.value.now

@Serializable
class Settings(val defaultParametersDefs: MutableReactiveList<ParameterDefObject> = reactiveList()) {
    fun getDefaultControlSpec(name: String) = defaultParametersDefs.now.find { p -> p.name.now == name }?.spec?.now

    companion object : PublicProperty<Settings> by publicProperty("SETTINGS") {
        fun createDefault(): Settings = Settings(ParameterDefObject.defaults.toReactiveList())
    }
}
