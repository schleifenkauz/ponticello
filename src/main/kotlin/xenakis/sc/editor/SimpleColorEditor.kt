package xenakis.sc.editor

import hextant.core.editor.SimpleEditor
import hextant.serial.string
import javafx.scene.paint.Color
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class SimpleColorEditor() : SimpleEditor<Color>() {
    constructor(initialColor: Color) : this() {
        setInitialResult(initialColor)
    }

    override fun fromJson(value: JsonElement): Color = Color.web(value.string)

    override fun toJson(value: Color): JsonElement {
        val str = value.toString()
        return JsonPrimitive(str)
    }
}