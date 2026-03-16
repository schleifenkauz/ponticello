package ponticello.model.record

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.div
import ponticello.impl.toDecimal
import ponticello.sc.NumericalControlSpec
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.nio.FloatBuffer
import java.util.*
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sqrt

@Serializable
class LoudnessThreshold(
    val db: ReactiveVariable<Decimal> = reactiveVariable(60.toDecimal()),
    val isEnabled: ReactiveVariable<Boolean> = reactiveVariable(true),
    val blockSize: ReactiveVariable<Int> = reactiveVariable(1024)
) {
    @Transient
    private val rmsList: Queue<Double> = LinkedList()

    @Transient
    private val peakList: Queue<Double> = LinkedList()

    fun get(): Double = when {
        isEnabled.now -> 10.0.pow(-(db.now / 20).toDouble())
        else -> 0.0
    }

    fun process(samples: List<FloatBuffer>, frames: Int): Boolean {
        var sum = 0.0
        var peak = 0.0
        for (i in 0 until frames) {
            val avg = (samples.sumOf { buf -> buf.get(i).toDouble() } / samples.size).absoluteValue
            if (avg > peak) peak = avg
            sum += avg * avg
        }
        val thresh = get()
        val numBlocks = blockSize.now / frames
        rmsList.offer(sqrt(sum / frames))
        peakList.offer(peak)
        while (rmsList.size > numBlocks) rmsList.remove()
        while (peakList.size > numBlocks) peakList.remove()
        val passes = peakList.max() / 5 >= thresh || rmsList.max() >= thresh
        if (!passes) {
            peakList.clear()
            rmsList.clear()
        }
        return passes
    }

    companion object {
        fun default() = LoudnessThreshold()

        val SPEC = NumericalControlSpec(60.0, 0.0, 100.0, 1.0.toDecimal())
    }
}