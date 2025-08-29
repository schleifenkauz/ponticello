package ponticello.ui.live

import fxutils.button
import fxutils.prompt.CompoundPrompt
import fxutils.prompt.SimpleSearchableListView
import fxutils.setFixedWidth
import javafx.event.Event
import javafx.geometry.Side
import javafx.scene.control.Button
import javafx.scene.control.Spinner
import ponticello.impl.one
import ponticello.impl.sync
import ponticello.model.obj.MeterObject
import ponticello.model.obj.MeterReference
import ponticello.model.registry.MeterRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObject.ResizeMode
import ponticello.model.score.TimeUnit
import reaktive.value.now
import reaktive.value.reactiveVariable

class ScoreObjectResizeDialog(
    private val obj: ScoreObject,
    private var meter: MeterReference,
    private var durationUnit: TimeUnit,
) : CompoundPrompt<ResizeMode>(
    "Resize ${obj.name.now}", labelWidth = 150.0, confirmText = "Resize"
) {
    private val durationValue = reactiveVariable(obj.duration / (meter.get()?.getDuration(durationUnit) ?: one))

    val meterSelector = SimpleSearchableListView(
        obj.context[MeterRegistry].map(MeterObject::reference), "Choose meter"
    ).selectorButton(this::meter)
        .setFixedWidth(SELECTOR_WIDTH)

    private val durationUnitSelector = SimpleSearchableListView(TimeUnit.entries, "Choose duration unit")
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

    init {
        addItem("Meter", meterSelector)
        addItem("Duration unit", durationUnitSelector)
        addItem("Duration", durationValueInput)
    }

    override fun confirm(): ResizeMode = ResizeMode.Regular

    companion object {
        private const val SELECTOR_WIDTH = 120.0

        fun show(obj: ScoreObject, ev: Event?) {
            val dialog = ScoreObjectResizeDialog(obj)
            val resizeMode = dialog.showDialog(ev) ?: return
            obj.quantizationConfig.update(dialog.config)
            obj.resize(dialog.config.computeDuration(), obj.height, resizeMode, Side.RIGHT)
        }
    }
}