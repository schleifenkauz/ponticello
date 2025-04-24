package xenakis.sc

import reaktive.value.ReactiveValue
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.Envelope
import xenakis.model.score.controls.AttackReleaseControl
import xenakis.model.score.controls.EnvelopeControl
import xenakis.model.score.controls.UGenControl
import xenakis.model.score.controls.ValueControl
import java.util.*
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin

private const val TWO_PI = 2.0 * Math.PI

interface LFO {
    val range: Double get() = max - min

    val min: Double
    val max: Double

    val children: List<LFO> get() = emptyList()

    fun resolveDependencies(obj: ParameterizedObject) {
        for (child in children) {
            child.resolveDependencies(obj)
        }
    }

    fun isResolved(): Boolean = children.all { child -> child.isResolved() }

    fun dependsOn(parameter: String): Boolean = children.any { child -> child.dependsOn(parameter) }

    fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray)
}

data class ConstantLFO(val value: ReactiveValue<Double>) : LFO {
    constructor(value: Double) : this(reactiveValue(value))

    override val min: Double
        get() = value.now
    override val max: Double
        get() = value.now

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        Arrays.fill(dest, value.now)
    }
}

data class AddLFO(val left: LFO, val right: LFO) : LFO {
    override val min = left.min + right.min
    override val max = left.max + right.max

    override val children: List<LFO>
        get() = listOf(left, right)

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        left.generateValues(duration, sampleRate, dest)
        val rhs = DoubleArray(dest.size)
        right.generateValues(duration, sampleRate, rhs)
        for (i in dest.indices) {
            dest[i] += rhs[i]
        }
    }
}

data class MulLFO(val left: LFO, val right: LFO) : LFO {
    override val min: Double = minOf(left.min * right.min, left.min * right.max, left.max * right.min)
    override val max: Double = maxOf(left.max * right.max, left.min * left.max)

    override val children: List<LFO>
        get() = listOf(left, right)

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        left.generateValues(duration, sampleRate, dest)
        val rhs = DoubleArray(dest.size)
        right.generateValues(duration, sampleRate, rhs)
        for (i in dest.indices) {
            dest[i] *= rhs[i]
        }
    }
}

data class DivLFO(val left: LFO, val right: LFO) : LFO {
    override val min: Double = minOf(left.min / right.min, left.min / right.max, left.max / right.min)
    override val max: Double = maxOf(left.max / right.max, left.min / left.max)

    override val children: List<LFO>
        get() = listOf(left, right)

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        left.generateValues(duration, sampleRate, dest)
        val rhs = DoubleArray(dest.size)
        right.generateValues(duration, sampleRate, rhs)
        for (i in dest.indices) {
            dest[i] /= rhs[i]
        }
    }
}

data class SubLFO(val left: LFO, val right: LFO) : LFO {
    override val min: Double
        get() = left.min - right.max
    override val max: Double
        get() = left.max - right.min

    override val children: List<LFO>
        get() = listOf(left, right)

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        left.generateValues(duration, sampleRate, dest)
        val rhs = DoubleArray(dest.size)
        right.generateValues(duration, sampleRate, rhs)
        for (i in dest.indices) {
            dest[i] -= rhs[i]
        }
    }
}

data class NegLFO(val lfo: LFO) : LFO {
    override val min: Double
        get() = -lfo.max
    override val max: Double
        get() = -lfo.min
    override val children: List<LFO>
        get() = listOf(lfo)

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        lfo.generateValues(duration, sampleRate, dest)
        for (i in dest.indices) {
            dest[i] = -dest[i]
        }
    }
}

data class ReciprocalLFO(val lfo: LFO) : LFO {
    override val min: Double
        get() = 1.0 / lfo.max
    override val max: Double
        get() = 1.0 / lfo.min
    override val children: List<LFO>
        get() = listOf(lfo)

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        lfo.generateValues(duration, sampleRate, dest)
        for (i in dest.indices) {
            dest[i] = 1.0 / dest[i]
        }
    }
}

private inline fun generatePhaseSignal(
    frequency: LFO, initialPhase: Double, sampleRate: Int,
    dest: DoubleArray, f: (Int, Double) -> Double,
) {
    frequency.generateValues(dest.size.toDouble(), dest.size, dest)
    var phase = initialPhase
    val phaseFactor = TWO_PI / sampleRate
    for (i in dest.indices) {
        val phaseIncrement = phaseFactor * dest[i]
        dest[i] = f(i, phase)
        phase = (phase + phaseIncrement) % TWO_PI
    }
}

data class Sawtooth(val frequency: LFO, val initialPhase: Double) : LFO {
    override val children: List<LFO>
        get() = listOf(frequency)
    override val min: Double
        get() = -1.0
    override val max: Double
        get() = 1.0

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        generatePhaseSignal(frequency, initialPhase, sampleRate, dest) { _, phase -> (phase / Math.PI) - 1.0 }
    }
}

data class Pulse(val frequency: LFO, val width: LFO, val initialPhase: Double) : LFO {
    override val children: List<LFO>
        get() = listOf(frequency)

    override val min: Double
        get() = -1.0
    override val max: Double
        get() = 1.0

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        val widths = DoubleArray(dest.size)
        width.generateValues(widths.size.toDouble(), sampleRate, widths)
        generatePhaseSignal(frequency, initialPhase, sampleRate, dest) { i, phase ->
            if (phase < Math.PI * widths[i]) 1.0 else -1.0
        }
    }
}

data class Sine(val frequency: LFO, val initialPhase: Double) : LFO {
    override val min: Double
        get() = -1.0
    override val max: Double
        get() = 1.0

    override val children: List<LFO>
        get() = listOf(frequency)

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        generatePhaseSignal(frequency, initialPhase, sampleRate, dest) { _, phase -> sin(phase) }
    }
}

data class Line(val start: Double, val end: Double) : LFO {
    override val min: Double
        get() = minOf(start, end)
    override val max: Double
        get() = maxOf(start, end)

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        val step = (end - start) / (dest.size - 1)
        var value = start
        for (i in dest.indices) {
            dest[i] = value
            value += step
        }
    }
}

data class LinRange(val lfo: LFO, override val min: Double, override val max: Double) : LFO {
    override val children: List<LFO>
        get() = listOf(lfo)

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        lfo.generateValues(duration, sampleRate, dest)
        val targetRange = max - min
        val factor = targetRange / lfo.range
        for (i in dest.indices) {
            dest[i] = dest[i] * factor - lfo.min + min
        }
    }
}

data class ExpRange(val lfo: LFO, override val min: Double, override val max: Double) : LFO {
    override val children: List<LFO>
        get() = listOf(lfo)

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        lfo.generateValues(duration, sampleRate, dest)
        val logMin = ln(min)
        val logMax = ln(max)
        val logRange = (logMax - logMin)
        val factor = logRange / lfo.range
        for (i in dest.indices) {
            val v = dest[i] - lfo.min
            dest[i] = exp(logMin + v * factor)
        }
    }
}

data class EnvelopeLFO(val envelope: Envelope) : LFO {
    override val min: Double = envelope.points.minOf { p -> p.value.value }
    override val max: Double = envelope.points.minOf { p -> p.value.value }

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        val points = envelope.points

        var currentPointIndex = 0
        var nextPointIndex = 1

        for (i in dest.indices) {
            val time = i.toDouble() / sampleRate

            while (nextPointIndex < points.size &&
                points[nextPointIndex].time.value <= time
            ) {
                currentPointIndex = nextPointIndex
                nextPointIndex++
            }

            if (nextPointIndex >= points.size) {
                dest[i] = points.last().value.value
                continue
            }

            val currentPoint = points[currentPointIndex]
            val nextPoint = points[nextPointIndex]
            val timeDiff = nextPoint.time.value - currentPoint.time.value
            val ratio = if (timeDiff > 0) {
                (time - currentPoint.time.value) / timeDiff
            } else {
                0.0
            }

            dest[i] = currentPoint.value.value +
                    ratio * (nextPoint.value.value - currentPoint.value.value)
        }
    }
}

class ParameterReferenceLFO(private val parameter: String) : LFO {
    private var resolved: ReactiveValue<LFO?>? = null

    override val min: Double
        get() = resolved?.now?.min ?: error("Parameter $parameter has not been resolved")
    override val max: Double
        get() = resolved?.now?.max ?: error("Parameter $parameter has not been resolved")

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        if (resolved == null) {
            error("Parameter $parameter has not been resolved")
        } else if (resolved!!.now == null) {
            error("Parameter $parameter has not been resolved as an LFO")
        }
        resolved!!.now!!.generateValues(duration, sampleRate, dest)
    }

    override fun resolveDependencies(obj: ParameterizedObject) {
        val control = obj.controls.getOrNull(parameter) ?: return
        resolved = when (val ctrl = control.now) {
            is AttackReleaseControl -> reactiveValue(EnvelopeLFO(ctrl.generateEnvelope(obj).points))
            is EnvelopeControl -> reactiveValue(EnvelopeLFO(ctrl.points))
            is UGenControl -> {
                ctrl.expr.editor.result.map { expr ->
                    expr.lfo?.also { lfo -> lfo.resolveDependencies(obj) }
                }
            }

            is ValueControl -> reactiveValue(ConstantLFO(ctrl.value.map { it.value }))
            else -> null
        }
    }

    override fun isResolved(): Boolean = resolved?.now != null && resolved!!.now!!.isResolved()

    override fun dependsOn(parameter: String): Boolean =
        parameter == this.parameter || resolved?.now?.dependsOn(parameter) == true
}