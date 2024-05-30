package xenakis.model

import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.list.ReactiveList
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import xenakis.impl.SuperColliderClient

@Serializable
sealed interface SynthDefObject : ParameterizedObject {
    val name: ReactiveValue<String>
    val color: ReactiveVariable<Color>
    val parameters: ReactiveList<ParameterDefObject>

    fun initialize(registry: SynthDefRegistry) {}

    fun SuperColliderClient.sync() {}

    fun SuperColliderClient.removeSynthDef() {}

    override fun getParameter(name: String): ParameterDefObject = parameters.now.find { it.name.now == name }
        ?: error("Parameter $name not found in SynthDef '${this.name.now}'")

    fun defaultControls() = parameters.now.associateTo(mutableMapOf()) { p -> p.name.now to p.defaultControl() }
}