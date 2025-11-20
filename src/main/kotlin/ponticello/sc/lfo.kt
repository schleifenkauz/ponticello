package ponticello.sc

import ponticello.model.registry.ObjectReference
import ponticello.model.score.Envelope
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl
import ponticello.ui.misc.LFOsManager
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveValue
import java.util.*
import kotlin.math.*

private const val TWO_PI = 2.0 * Math.PI

abstract class LFO {
    val range: Double get() = max - min

    abstract val min: Double
    abstract val max: Double

    open val children: List<LFO> get() = emptyList()

    protected lateinit var manager: LFOsManager
        private set

    fun initialize(manager: LFOsManager) {
        this.manager = manager
        children.forEach { child -> child.initialize(manager) }
    }

    open fun isResolved(): Boolean = children.all { child -> child.isResolved() }

    open fun dependsOn(parameter: NamedParameterControl): Boolean = children.any { child -> child.dependsOn(parameter) }

    abstract fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray)
}

data class ConstantLFO(val value: ReactiveValue<Double>) : LFO() {
    constructor(value: Double) : this(reactiveValue(value))

    override val min: Double
        get() = value.now
    override val max: Double
        get() = value.now

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        Arrays.fill(dest, value.now)
    }
}

data class AddLFO(val left: LFO, val right: LFO) : LFO() {
    override val min get() = left.min + right.min
    override val max get() = left.max + right.max

    override val children = listOf(left, right)

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        left.generateValues(duration, sampleRate, dest)
        val rhs = DoubleArray(dest.size)
        right.generateValues(duration, sampleRate, rhs)
        for (i in dest.indices) {
            dest[i] += rhs[i]
        }
    }
}

data class MulLFO(val left: LFO, val right: LFO) : LFO() {
    override val min: Double by lazy { minOf(left.min * right.min, left.min * right.max, left.max * right.min) }
    override val max: Double by lazy { maxOf(left.max * right.max, left.min * left.max) }

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

data class DivLFO(val left: LFO, val right: LFO) : LFO() {
    override val min: Double by lazy { minOf(left.min / right.min, left.min / right.max, left.max / right.min) }
    override val max: Double by lazy { maxOf(left.max / right.max, left.min / left.max) }

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

data class SubLFO(val left: LFO, val right: LFO) : LFO() {
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

data class NegLFO(val lfo: LFO) : LFO() {
    override val min: Double
        get() = -lfo.max
    override val max: Double
        get() = -lfo.min
    override val children = listOf(lfo)

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        lfo.generateValues(duration, sampleRate, dest)
        for (i in dest.indices) {
            dest[i] = -dest[i]
        }
    }
}

data class ReciprocalLFO(val lfo: LFO) : LFO() {
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
    frequency: LFO, initialPhase: Double, duration: Double, sampleRate: Int,
    dest: DoubleArray, f: (Int, Double) -> Double,
) {
    frequency.generateValues(duration, sampleRate, dest)
    var phase = initialPhase
    val phaseFactor = TWO_PI / sampleRate
    for (i in dest.indices) {
        val phaseIncrement = phaseFactor * dest[i]
        dest[i] = f(i, phase)
        phase = (phase + phaseIncrement) % TWO_PI
    }
}

data class Sawtooth(val frequency: LFO, val initialPhase: Double) : LFO() {
    override val children = listOf(frequency)
    override val min: Double
        get() = -1.0
    override val max: Double
        get() = 1.0

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        generatePhaseSignal(frequency, initialPhase, duration, sampleRate, dest) { _, phase -> (phase / Math.PI) - 1.0 }
    }
}

data class Pulse(val frequency: LFO, val width: LFO, val initialPhase: Double) : LFO() {
    override val children: List<LFO>
        get() = listOf(frequency)

    override val min: Double
        get() = -1.0
    override val max: Double
        get() = 1.0

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        val widths = DoubleArray(dest.size)
        width.generateValues(widths.size.toDouble(), sampleRate, widths)
        generatePhaseSignal(frequency, initialPhase, duration, sampleRate, dest) { i, phase ->
            if (phase < Math.PI * widths[i]) 1.0 else -1.0
        }
    }
}

data class Sine(val frequency: LFO, val initialPhase: Double) : LFO() {
    override val min: Double
        get() = -1.0
    override val max: Double
        get() = 1.0

    override val children: List<LFO>
        get() = listOf(frequency)

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        generatePhaseSignal(frequency, initialPhase, duration, sampleRate, dest) { _, phase -> sin(phase) }
    }
}

data class Tri(val frequency: LFO, val initialPhase: Double) : LFO() {
    override val min: Double
        get() = -1.0
    override val max: Double
        get() = 1.0

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        generatePhaseSignal(frequency, initialPhase, duration, sampleRate, dest) { _, phase ->
            2 / PI * asin(sin(phase))
        }
    }
}

class WhiteNoise : LFO() {
    private val random: Random = Random()

    override val min: Double
        get() = -1.0
    override val max: Double
        get() = 1.0

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        for (i in dest.indices) {
            dest[i] = random.nextDouble(-1.0, 1.0)
        }
    }
}

data class LFNoise0(val frequency: LFO): LFO() {
    private val random = Random()

    override val min: Double get() = -1.0
    override val max: Double get() = 1.0

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        var value = random.nextDouble(-1.0, 1.0)
        frequency.generateValues(duration, sampleRate, dest)
        var phase = 0.0
        val phaseFactor = 1.0 / sampleRate
        for (i in dest.indices) {
            val phaseIncrement = phaseFactor * dest[i]
            dest[i] = value
            phase = (phase + phaseIncrement)
            if (phase >= 1.0) {
                value = random.nextDouble(-1.0, 1.0)
                phase %= 1.0
            }
        }
    }
}

data class LFNoise1(val frequency: LFO): LFO() {
    private val random = Random()

    override val min: Double get() = -1.0
    override val max: Double get() = 1.0

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        var prev = random.nextDouble(-1.0, 1.0)
        var next = random.nextDouble(-1.0, 1.0)
        frequency.generateValues(duration, sampleRate, dest)
        var phase = 0.0
        val phaseFactor = 1.0 / sampleRate
        for (i in dest.indices) {
            val phaseIncrement = phaseFactor * dest[i]
            dest[i] = (prev * (1.0 - phase) + next * phase) / 2.0
            phase = (phase + phaseIncrement)
            if (phase >= 1.0) {
                prev = next
                next = random.nextDouble(-1.0, 1.0)
                phase %= 1.0
            }
        }
    }
}

data class Line(val start: Double, val end: Double) : LFO() {
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

data class LinRange(val lfo: LFO, val minValue: LFO, val maxValue: LFO) : LFO() {
    override val children: List<LFO>
        get() = listOf(lfo, minValue, maxValue)

    override val min: Double
        get() = minValue.min
    override val max: Double
        get() = maxValue.max

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        lfo.generateValues(duration, sampleRate, dest)
        val min = DoubleArray(dest.size)
        minValue.generateValues(duration, sampleRate, min)
        val max = DoubleArray(dest.size)
        maxValue.generateValues(duration, sampleRate, max)
        for (i in dest.indices) {
            val targetRange = max[i] - min[i]
            val factor = targetRange / lfo.range
            dest[i] = (dest[i] - lfo.min) * factor + min[i]
        }
    }
}

data class ExpRange(val lfo: LFO, val minValue: LFO, val maxValue: LFO) : LFO() {
    override val children: List<LFO>
        get() = listOf(lfo, minValue, maxValue)

    override val min: Double
        get() = minValue.min
    override val max: Double
        get() = maxValue.max

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        lfo.generateValues(duration, sampleRate, dest)
        val min = DoubleArray(dest.size)
        minValue.generateValues(duration, sampleRate, min)
        val max = DoubleArray(dest.size)
        maxValue.generateValues(duration, sampleRate, max)
        for (i in dest.indices) {
            val logMin = ln(min[i])
            val logMax = ln(max[i])
            val logRange = (logMax - logMin)
            val factor = logRange / lfo.range
            val v = dest[i] - lfo.min
            dest[i] = exp(logMin + v * factor)
        }
    }
}

data class EnvelopeLFO(val envelope: Envelope) : LFO() {
    override val min: Double get() = envelope.points.minOf { p -> p.value.value }
    override val max: Double get() = envelope.points.minOf { p -> p.value.value }

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

class ParameterReferenceLFO(private val parameter: ObjectReference<NamedParameterControl>) : LFO() {
    private val resolvedLFO get() = parameter.get()?.let { param -> manager.getLFO(param) }

    override val min: Double
        get() = resolvedLFO?.min ?: error("Parameter $parameter has not been resolved")
    override val max: Double
        get() = resolvedLFO?.max ?: error("Parameter $parameter has not been resolved")

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        val lfo = resolvedLFO ?: error("Parameter $parameter has not been resolved")
        lfo.generateValues(duration, sampleRate, dest)
    }

    override fun isResolved(): Boolean = resolvedLFO != null

    override fun dependsOn(parameter: NamedParameterControl): Boolean =
        parameter == this.parameter.get() || resolvedLFO?.dependsOn(parameter) == true
}

class ParameterNameLFO(private val parameter: String) : LFO() {
    private val resolvedLFO get() = manager.getLFOByName(parameter)

    override val min: Double
        get() = resolvedLFO?.min ?: error("Parameter $parameter has not been resolved")
    override val max: Double
        get() = resolvedLFO?.max ?: error("Parameter $parameter has not been resolved")

    override fun generateValues(duration: Double, sampleRate: Int, dest: DoubleArray) {
        val lfo = resolvedLFO ?: error("Parameter $parameter has not been resolved")
        lfo.generateValues(duration, sampleRate, dest)
    }

    override fun isResolved(): Boolean = resolvedLFO != null

    override fun dependsOn(parameter: NamedParameterControl): Boolean = parameter.name.now == this.parameter
}