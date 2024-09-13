package xenakis.model

import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable

@Serializable
sealed interface InstrumentObject : NamedObject, SuperColliderObject {
    val color: ReactiveVariable<Color>
}