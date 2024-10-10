package xenakis.impl

import javafx.scene.paint.Color
import kotlinx.serialization.json.*
import java.io.File

fun JsonObject.getDouble(name: String) = get(name)?.jsonPrimitive?.double
fun JsonObject.getInt(name: String) = get(name)?.jsonPrimitive?.int
fun JsonObject.getString(name: String) = get(name)?.jsonPrimitive?.content
fun JsonObject.getBoolean(name: String) = get(name)?.jsonPrimitive?.boolean
fun JsonObject.getFile(name: String) = File(getString(name) ?: "")
fun JsonObject.getColor(name: String) = getString(name)?.let { str -> Color.web(str) }
inline fun <reified T> JsonObject.getSerializableValue(name: String) =
    get(name)?.let { obj -> Json.decodeFromJsonElement<T>(obj) }

inline fun <reified T> JsonObjectBuilder.putSerializableValue(name: String, value: T) {
    put(name, Json.encodeToJsonElement(value))
}