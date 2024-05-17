package xenakis.model

import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import xenakis.impl.ColorSerializer

@Serializable
class MemoObject(
    override var name: String,
    var text: String,
    override var start: Double, var width: Double,
    override var y: Double, override var height: Double,
    @Serializable(with = ColorSerializer::class) override var color: Color = Color.BLACK,
) : ScoreObject() {
    override var duration: Double
        get() = 0.0
        set(value) {
            throw UnsupportedOperationException("Cannot set duration of MemoObject")
        }
    override var muted: Boolean
        get() = false
        set(value) {
            throw UnsupportedOperationException("Cannot mute MemoObject")
        }

    override val controls: List<ParameterControl>
        get() = emptyList()

    override fun clone(newName: String): ScoreObject = MemoObject(newName, text, start, duration, y, height, color)
}