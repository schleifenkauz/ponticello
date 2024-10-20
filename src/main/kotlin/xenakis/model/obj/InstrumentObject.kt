package xenakis.model.obj

import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import xenakis.model.registry.NamedObject

@Serializable
sealed interface InstrumentObject : NamedObject, SuperColliderObject {
    val color: ReactiveVariable<Color>

    val canCopy get() = true

    fun copy(name: String): InstrumentObject
}