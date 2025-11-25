package ponticello.model.record

import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.div
import ponticello.impl.toDecimal
import ponticello.sc.NumericalControlSpec
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.nio.FloatBuffer
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sqrt

@Serializable
class LoudnessThreshold(
    val db: ReactiveVariable<Decimal>,
    val isEnabled: ReactiveVariable<Boolean>
) {
    fun get(): Double = when {
        isEnabled.now -> 10.0.pow(-(db.now / 20).toDouble())
        else -> 0.0
    }

    fun passes(samples: List<FloatBuffer>, frames: Int): Boolean {
        var sum = 0.0
        var peak = 0.0
        for (i in 0 until frames) {
            val avg = (samples.sumOf { buf -> buf.get(i).toDouble() } / samples.size).absoluteValue
            if (avg > peak) peak = avg
            sum += avg * avg
        }
        val rms = sqrt(sum / frames)
        val thresh = get()
        return peak / 5 >= thresh || rms >= thresh
    }

    companion object {
        fun default() = LoudnessThreshold(
            db = reactiveVariable(60.toDecimal()),
            isEnabled = reactiveVariable(true)
        )

        val SPEC = NumericalControlSpec(60.0, 0.0, 100.0, 1.0.toDecimal())
    }
}