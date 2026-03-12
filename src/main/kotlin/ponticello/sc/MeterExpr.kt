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
        val meterVar = meter.superColliderName
        val method = type.name.replaceFirstChar { it.lowercase() }
        writer.append(meterVar, ".", method)
    }

    override fun getLfo(): LFO? {
        val meter = meterReference.get() ?: return null
        val value = when (type) {
            BarDur -> meter.getDuration(TimeUnit.Bars)
            BeatDur -> meter.getDuration(TimeUnit.Beats)
            TickDur -> meter.getDuration(TimeUnit.Ticks)
            BarsRate -> 1 / meter.getDuration(TimeUnit.Bars)
            BeatRate -> 1 / meter.getDuration(TimeUnit.Beats)
            TickRate -> 1 / meter.getDuration(TimeUnit.Ticks)
        }
        return ConstantLFO(value.value)
    }

    enum class Type {
        BarDur, BeatDur, TickDur,
        BarsRate, BeatRate, TickRate;
    }
}