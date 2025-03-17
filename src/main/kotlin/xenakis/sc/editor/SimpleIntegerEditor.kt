package xenakis.sc.editor

import hextant.core.editor.SimpleEditor
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

class SimpleIntegerEditor() : SimpleEditor<Int>() {
    constructor(value: Int): this() {
        setInitialResult(value)
    }

    override fun fromJson(value: JsonElement): Int = value.jsonPrimitive.int

    override fun toJson(value: Int): JsonElement = JsonPrimitive(value)
}