package xenakis.impl

import hextant.context.Context
import hextant.context.Properties.classLoader
import hextant.plugins.Aspects
import hextant.plugins.Implementation
import javafx.scene.paint.Color
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.io.File
import java.io.StringWriter

typealias DoubleRange = ClosedFloatingPointRange<Double>

infix fun DoubleRange.step(step: Double) = sequence {
    var value = start
    while (value <= endInclusive) {
        yield(value)
        value += step
    }
}

fun Context.registerImplementationsFromClasspath() {
    val cl = this[classLoader]
    for (impls in cl.getResources("implementations.json")) {
        val implementations: List<Implementation> = Json.decodeFromString(impls.readText())
        for (impl in implementations) {
            this[Aspects].addImplementation(impl, cl)
        }
    }
}

inline fun code(writeCode: ScWriter.() -> Unit): String {
    val writer = StringWriter()
    ScWriter(writer).appendGroup(writeCode)
    val code = writer.toString()
    return code
}

val File.superColliderPath get() = "\"" + absolutePath.replace('\\', '/') + "\""

fun JsonObject.getDouble(name: String) = get(name)?.jsonPrimitive?.double

fun JsonObject.getString(name: String) = get(name)?.jsonPrimitive?.content

fun JsonObject.getBoolean(name: String) = get(name)?.jsonPrimitive?.boolean

fun JsonObject.getFile(name: String) = File(getString(name) ?: "")

fun JsonObject.getColor(name: String) = getString(name)?.let { str -> Color.web(str) }

inline fun <reified T> JsonObject.getSerializableValue(name: String) =
    get(name)?.let { obj -> Json.decodeFromJsonElement<T>(obj) }

inline fun <reified T> JsonObjectBuilder.putSerializableValue(name: String, value: T) {
    put(name, Json.encodeToJsonElement(value))
}