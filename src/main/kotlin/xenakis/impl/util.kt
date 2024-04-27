package xenakis.impl

import hextant.context.Context
import hextant.context.Properties.classLoader
import hextant.plugins.Aspects
import hextant.plugins.Implementation
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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