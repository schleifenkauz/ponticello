package xenakis.impl

import hextant.context.Context
import hextant.context.Properties.classLoader
import hextant.plugins.Aspects
import hextant.plugins.Implementation
import javafx.scene.control.Spinner
import javafx.scene.paint.Color
import kotlinx.serialization.json.Json
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.sc.Warp
import xenakis.sc.client.ScWriter
import java.io.File
import java.io.StringWriter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.sound.sampled.AudioInputStream
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0

fun Context.registerImplementationsFromClasspath() {
    val cl = this[classLoader]
    for (impls in cl.getResources("implementations.json")) {
        val implementations: List<Implementation> = Json.decodeFromString(impls.readText())
        for (impl in implementations) {
            this[Aspects].addImplementation(impl, cl)
        }
    }
}

inline fun writeCode(writeCode: ScWriter.() -> Unit): String {
    val writer = StringWriter()
    try {
        ScWriter(writer).appendGroup(writeCode)
    } catch (e: Exception) {
        Logger.error("Error during code generation", e)
        return ""
    }
    val code = writer.toString()
    return code
}

val File.superColliderPath get() = "\"" + absoluteFile.invariantSeparatorsPath + "\""

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

fun String.toWarp() = when (this) {
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

val isWindows get() = System.getProperty("os.name").contains("Windows")

val canSuperColliderTalkToMe get() = true

fun String.replacePrefix(prefix: String, replacement: String) =
    if (startsWith(prefix)) replacement + drop(prefix.length) else this

fun Spinner<Double>.sync(variable: ReactiveVariable<Decimal>): Spinner<Double> {
    isEditable = true
    valueProperty().addListener { _ ->
        val value = editor.text.parseDecimal() ?: return@addListener
        variable.set(value)
    }
    userData = variable.observe { _, _, v -> editor.text = v.toString() }
    return this
}