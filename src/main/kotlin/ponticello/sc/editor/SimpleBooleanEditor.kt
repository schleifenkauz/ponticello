package ponticello.sc.editor

import hextant.core.editor.SimpleEditor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

class SimpleBooleanEditor(value: Boolean) : SimpleEditor<Boolean>() {
    init {
        setInitialResult(value)
    }

    constructor() : this(false)

    override fun toJson(value: Boolean): JsonElement = JsonPrimitive(value)

    override fun fromJson(value: JsonElement): Boolean = value.jsonPrimitive.boolean
}