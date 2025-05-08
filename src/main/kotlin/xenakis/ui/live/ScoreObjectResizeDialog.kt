package xenakis.ui.live

import fxutils.button
import fxutils.prompt.CompoundPrompt
import fxutils.prompt.SimpleSearchableListView
import fxutils.setFixedWidth
import javafx.event.Event
import javafx.geometry.HorizontalDirection
import javafx.scene.control.Button
import javafx.scene.control.Spinner
import reaktive.value.now
import xenakis.impl.sync
import xenakis.model.registry.MeterRegistry
import xenakis.model.registry.reference
import xenakis.model.score.ScoreObject
import xenakis.model.score.ScoreObject.ResizeMode
import xenakis.model.score.TimeUnit
import xenakis.ui.impl.Direction

class ScoreObjectResizeDialog(private val obj: ScoreObject) : CompoundPrompt<ResizeMode>(
    "Resize ${obj.name.now}",
    labelWidth = 150.0,
    confirmText = "Resize"
) {
    private val meters = obj.context[MeterRegistry].map { obj -> obj.reference() }
    private val config = obj.quantizationConfig.copy()

    private val meterSelector = SimpleSearchableListView(meters, "Choose meter")
        .selectorButton(config.meter)
        .setFixedWidth(SELECTOR_WIDTH)

    private val durationUnitSelector = SimpleSearchableListView(TimeUnit.entries, "Choose duration unit")
        .selectorButton(config.durationUnit)
        .setFixedWidth(SELECTOR_WIDTH)

    private val durationValueInput = Spinner<Double>(0.0, Double.MAX_VALUE, config.durationValue.now.value)
        .sync(config.durationValue)
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
            val direction = Direction.horizontal(HorizontalDirection.RIGHT)
            obj.resize(dialog.config.computeDuration(), obj.height, resizeMode, direction)
        }
    }
}