package ponticello.ui.misc

import fxutils.button
import fxutils.prompt.CompoundPrompt
import fxutils.prompt.SimpleSelectorPrompt
import fxutils.setFixedWidth
import javafx.event.Event
import javafx.geometry.Side
import javafx.scene.control.Button
import javafx.scene.control.Spinner
import ponticello.impl.Decimal
import ponticello.impl.one
import ponticello.impl.sync
import ponticello.model.live.QuantizationConfig
import ponticello.model.obj.MeterObject
import ponticello.model.obj.MeterReference
import ponticello.model.registry.MeterRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObject.ResizeMode
import ponticello.model.score.TimeUnit
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

class ScoreObjectResizeDialog(
    obj: ScoreObject, initialMeter: MeterReference,
) : CompoundPrompt<ResizeMode>("Resize ${obj.name.now}", labelWidth = 150.0, confirmText = "Resize") {
    private var meterRef = initialMeter
        set(value) {
            val oldMeter = field.get()
            val newMeter = value.get()
            field = value
            if (oldMeter != null && newMeter != null) {
                durationValue.now *= (oldMeter.getDuration(durationUnit) / newMeter.getDuration(durationUnit))
            }
        }

    private var durationUnit: TimeUnit = TimeUnit.Seconds
        set(newUnit) {
            val oldUnit = field
            field = newUnit
            val meter = meterRef.get()
            if (meter != null) {
                durationValue.now *= (meter.getDuration(oldUnit) / meter.getDuration(newUnit))
            }
        }

    private val durationValue: ReactiveVariable<Decimal> = reactiveVariable(obj.duration)

    init {
        val meter = meterRef.get()
        if (meter != null) {
            val (unit, value) = meter.represent(obj.duration)
            durationUnit = unit
            durationValue.now = value
        }
    }

    val meterSelector = SimpleSelectorPrompt(
        obj.context[MeterRegistry].map(MeterObject::reference), "Choose meter"
    ).selectorButton(this::meterRef)
        .setFixedWidth(SELECTOR_WIDTH)

    private val durationUnitSelector = SimpleSelectorPrompt(TimeUnit.entries, "Choose duration unit")
        .selectorButton(this::durationUnit)
        .setFixedWidth(SELECTOR_WIDTH)

    private val durationValueInput = Spinner<Double>(0.0, Double.MAX_VALUE, durationValue.now.value)
        .sync(durationValue)
        .setFixedWidth(120.0)

    override fun extraButtons(): List<Button> = listOf(
        button("Stretch") {
            commit(ResizeMode.Stretch)
        }
    )

    private fun computeDuration() = durationValue.now * (meterRef.get()?.getDuration(durationUnit) ?: one)

    init {
        addItem("Meter", meterSelector)
        addItem("Duration unit", durationUnitSelector)
        addItem("Duration", durationValueInput)
    }

    override fun confirm(): ResizeMode = ResizeMode.Regular

    companion object {
        private const val SELECTOR_WIDTH = 120.0

        fun show(obj: ScoreObject, quantization: QuantizationConfig, ev: Event?) {
            val dialog = ScoreObjectResizeDialog(obj, quantization.meter.now)
            val resizeMode = dialog.showDialog(ev) ?: return
            obj.resize(dialog.computeDuration(), obj.height, resizeMode, Side.RIGHT)
        }
    }
}