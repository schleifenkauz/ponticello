package ponticello.scapi

import ponticello.scsynth.Rate

internal fun createControlUGen(parameterName: String, rate: Rate, defaults: FloatArray): UGen {
    val ugen = ControlUGen(parameterName, rate, defaults.asList())
    return when (defaults.size) {
        0 -> error("Control parameter $parameterName must have at least one default value")
        1 -> OutputProxy(ugen, -1)
        else -> MultiChannelUGen(defaults.indices.map { idx -> OutputProxy(ugen, idx) })
    }
}

internal inline fun expand1(uGen: UGen, op: (UGen) -> UGen) = when (uGen) {
    is MultiChannelUGen -> MultiChannelUGen(uGen.channels.map(op))
    else -> op(uGen)
}

internal fun binOp(left: UGen, right: UGen, operator: BinaryOperator) =
    expand2(left, right) { l, r -> BinaryOpUGen(operator, l, r) }

internal fun unaryOp(input: UGen, operator: UnaryOperator) =
    expand1(input) { UnaryOpUgen(operator, it) }

private inline fun zip2(l1: List<UGen>, l2: List<UGen>, op: (UGen, UGen) -> UGen): List<UGen> = buildList {
    for (i in 0 until maxOf(l1.size, l2.size)) {
        val u1 = l1[i % l1.size]
        val u2 = l2[i % l2.size]
        add(op(u1, u2))
    }
}

private inline fun zip3(l1: List<UGen>, l2: List<UGen>, l3: List<UGen>, op: (UGen, UGen, UGen) -> UGen): List<UGen> =
    buildList {
        for (i in 0 until maxOf(l1.size, l2.size, l3.size)) {
            val u1 = l1[i % l1.size]
            val u2 = l2[i % l2.size]
            val u3 = l3[i % l3.size]
            add(op(u1, u2, u3))
        }
    }

private inline fun expand2(u1: UGen, u2: UGen, op: (UGen, UGen) -> UGen) = when {
    u1 is MultiChannelUGen && u2 is MultiChannelUGen -> MultiChannelUGen(zip2(u1.channels, u2.channels, op))
    u1 is MultiChannelUGen -> MultiChannelUGen(u1.channels.map { op(it, u2) })
    u2 is MultiChannelUGen -> MultiChannelUGen(u2.channels.map { op(u1, it) })
    else -> op(u1, u2)
}

private inline fun expand3(u1: UGen, u2: UGen, u3: UGen, crossinline op: (UGen, UGen, UGen) -> UGen): UGen {
    val mc1 = u1 is MultiChannelUGen
    val mc2 = u2 is MultiChannelUGen
    val mc3 = u3 is MultiChannelUGen
    return when {
        mc1 && mc2 && mc3 -> MultiChannelUGen(zip3(u1.channels, u2.channels, u3.channels, op))
        mc1 && mc2 -> MultiChannelUGen(zip2(u1.channels, u2.channels) { x, y -> op(x, y, y) })
        mc1 && mc3 -> MultiChannelUGen(zip2(u1.channels, u3.channels) { x, y -> op(x, u2, y) })
        mc2 && mc3 -> MultiChannelUGen(zip2(u2.channels, u3.channels) { x, y -> op(u1, x, y) })
        mc1 -> MultiChannelUGen(u1.channels.map { op(it, u2, u3) })
        mc2 -> MultiChannelUGen(u2.channels.map { op(u1, it, u3) })
        mc3 -> MultiChannelUGen(u3.channels.map { op(u1, u2, it) })
        else -> op(u1, u2, u3)
    }
}

private fun expand2(u1: UGen, u2: UGen, className: String, outputRates: List<Rate>) =
    expand2(u1, u2) { x, y -> RegularUGen(className, Rate.Audio, listOf(x, y), outputRates) }

private inline fun expandN(list: List<UGen>, op: (List<UGen>) -> UGen): UGen {
    val maxSize = list.maxOf { u -> if (u is MultiChannelUGen) u.channels.size else 1 }
    if (maxSize == 1) return op(list.map { u -> if (u is MultiChannelUGen) u.channels[0] else u })
    return MultiChannelUGen(List(maxSize) { i ->
        val components = list.map { u ->
            if (u is MultiChannelUGen) u.channels[i % u.channels.size] else u
        }
        op(components)
    })
}

internal fun createRegularUgen(
    className: String, rate: Rate, inputs: Array<out UGen>, outputs: Int
): UGen = expandN(inputs.asList()) { ugens ->
    val ugen = RegularUGen(className, rate, ugens, List(outputs) { rate })
    if (outputs == 1) ugen
    else MultiChannelUGen(List(outputs) { idx -> OutputProxy(ugen, idx) })
}

internal fun kr(className: String, vararg inputs: UGen, outputs: Int = 1) =
    createRegularUgen(className, Rate.Control, inputs, outputs)

internal fun ar(className: String, vararg inputs: UGen, outputs: Int = 1) =
    createRegularUgen(className, Rate.Audio, inputs, outputs)
