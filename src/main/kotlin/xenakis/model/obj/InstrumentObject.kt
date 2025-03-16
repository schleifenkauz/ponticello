package xenakis.model.obj

import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.registry.NamedObject
import xenakis.model.registry.ObjectRegistry

@Serializable
sealed interface InstrumentObject : NamedObject, SuperColliderObject {
    val color: ReactiveVariable<Color>

    override val registry: ObjectRegistry<*>?
        get() = context[InstrumentRegistry]
}