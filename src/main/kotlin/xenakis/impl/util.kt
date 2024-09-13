package xenakis.impl

import com.illposed.osc.OSCMessage
import hextant.context.Context
import hextant.context.Properties.classLoader
import hextant.plugins.Aspects
import hextant.plugins.Implementation
import javafx.scene.paint.Color
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import reaktive.value.ReactiveValue
import reaktive.value.reactiveVariable
import xenakis.sc.Warp
import java.io.File
import java.io.StringWriter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioInputStream
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0

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

val File.superColliderPath get() = "\"" + absoluteFile.invariantSeparatorsPath + "\""

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

fun <T> unsupported(): ReadWriteProperty<Any?, T> = object : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T =
        throw UnsupportedOperationException("$property is unsupported")

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        throw UnsupportedOperationException("$property is unsupported")
    }
}

operator fun <R> (() -> KMutableProperty0<R>).setValue(thisRef: Any, property: KProperty<*>, value: R) {
    invoke().set(value)
}

operator fun <R> (() -> KProperty0<R>).getValue(thisRef: Any, property: KProperty<*>): R = invoke().get()

private val defaultColors = listOf("red", "green", "blue", "white", "orange", "purple", "cyan")

fun randomColor() = Color.web(defaultColors.random())

val OSCMessage.boolean get() = arguments[1] as Int != 0
val OSCMessage.double get() = (arguments[1] as Float).toDouble()
val OSCMessage.string get() = arguments[1] as String
val OSCMessage.integer get() = arguments[1] as Int
val OSCMessage.warp
    get() = when (arguments[1] as String) {
        "A LinearWarp" -> Warp.Linear
        "An ExponentialWarp" -> Warp.Exponential
        else -> Warp.Linear
    }

val AudioInputStream.duration get() = (frameLength / format.frameRate).toDouble()

inline fun async(timeLimit: Long = 1000, crossinline code: () -> Unit) {
    CompletableFuture.supplyAsync { code() }
        .orTimeout(timeLimit, TimeUnit.MILLISECONDS)
        .exceptionally { e -> e.printStackTrace() }
}

inline fun <V> KMutableProperty0<V>.reactive(crossinline onUpdate: (oldValue: V, newValue: V) -> Unit) =
    object : ReadWriteProperty<Any?, V> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): V = get()

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
            val oldValue = get()
            if (oldValue == value) return
            set(value)
            onUpdate(oldValue, value)
        }
    }

fun <T> ReactiveValue<T>.copy() = reactiveVariable(get())

val isMyComputerDumb get() = System.getProperty("os.name").contains("Windows")

fun String.replacePrefix(prefix: String, replacement: String) =
    if (startsWith(prefix)) replacement + drop(prefix.length) else this