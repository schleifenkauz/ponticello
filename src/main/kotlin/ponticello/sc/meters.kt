package ponticello.sc

import hextant.context.Context
import ponticello.impl.div
import ponticello.model.obj.MeterReference
import ponticello.model.score.TimeUnit
import ponticello.sc.MeterExpr.Type.*
import ponticello.sc.client.ScWriter

data class MeterExpr(
    val type: Type,
    val meterReference: MeterReference
) : ScExpr {
    override val isValid: Boolean
        get() = meterReference.isValid

    override fun code(writer: ScWriter, context: Context) {
        val meter = meterReference.get()
        if (meter == null) {
            writer.append("nil /*unknown meter ${meterReference.getName()}*/")
            return
        }
        val meterDict = meter.superColliderName
        val expr = when (type) {
            BarDur -> "(60 / $meterDict[\\bpm] * $meterDict[\\bpb])"
            BeatDur -> "(60 / $meterDict[\\bpm])"
            TickDur -> "(60 / $meterDict[\\bpm] / $meterDict[\\tpb])"
            BarsPerSecond -> "($meterDict[\\bpm] / 60 / $meterDict[\\bpb])"
            BeatsPerSecond -> "($meterDict[\\bpm] / 60)"
            TicksPerSecond -> "($meterDict[\\bpm] / 60 * $meterDict[\\tpb])"
        }
        writer.append(expr)
    }

    override fun getLfo(): LFO? {
        val meter = meterReference.get() ?: return null
        val value = when (type) {
            BarDur -> meter.getDuration(TimeUnit.Bars)
            BeatDur -> meter.getDuration(TimeUnit.Beats)
            TickDur -> meter.getDuration(TimeUnit.Ticks)
            BarsPerSecond -> 1 / meter.getDuration(TimeUnit.Bars)
            BeatsPerSecond -> 1 / meter.getDuration(TimeUnit.Beats)
            TicksPerSecond -> 1 / meter.getDuration(TimeUnit.Ticks)
        }
        return ConstantLFO(value.value)
    }

    enum class Type {
        BarDur, BeatDur, TickDur,
        BarsPerSecond, BeatsPerSecond, TicksPerSecond;
    }
}