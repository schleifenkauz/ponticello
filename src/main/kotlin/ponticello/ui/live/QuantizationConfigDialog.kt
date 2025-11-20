package ponticello.ui.live

import fxutils.*
import fxutils.controls.CheckBox
import fxutils.controls.OptionSpinner
import fxutils.prompt.CompoundPrompt
import fxutils.prompt.SimpleSelectorPrompt
import fxutils.undo.UndoManager
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.layout.HBox
import ponticello.impl.Decimal
import ponticello.impl.one
import ponticello.impl.zero
import ponticello.model.live.QuantizationConfig
import ponticello.model.live.QuantizationUnit
import ponticello.model.player.ClockRegistry
import ponticello.model.player.MeterRegistry
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.model.score.ScoreObject.ResizeMode
import ponticello.model.score.TimeUnit
import ponticello.ui.impl.DecimalSpinner
import reaktive.value.binding.flatMap
import reaktive.value.binding.not
import reaktive.value.fx.asObservableValue

class QuantizationConfigDialog(
    config: QuantizationConfig, title: String,
) : CompoundPrompt<ResizeMode>(title, labelWidth = 55.0) {
    private val meters = config.context[MeterRegistry].map { obj -> obj.reference() }
    private val clocks = config.context[ClockRegistry].map { obj -> obj.reference() }

    private val meterSelector = SimpleSelectorPrompt(meters, "Choose meter")
        .selectorButton(config.meter)
        .setFixedWidth(SELECTOR_WIDTH)

    private val clockSelector = SimpleSelectorPrompt(clocks, "Choose clock")
        .selectorButton(config.clock)
        .setFixedWidth(SELECTOR_WIDTH)

    private val quantizationUnitInput = OptionSpinner(config.quantizationUnit, QuantizationUnit.entries)

    private val quantizationValueInput = DecimalSpinner(
        config.quantizationValue,
        min = zero, max = Decimal.INF,
        step = one, maxPrecision = 2
    ).minColumns(6)

    private val offsetUnitInput = OptionSpinner(config.offsetUnit, TimeUnit.entries)

    private val offsetValueInput = DecimalSpinner(
        config.offsetValue,
        min = zero, max = Decimal.INF,
        step = one, maxPrecision = 2
    ).minColumns(6)

    private val enableQuantizationToggle = CheckBox(config.enableQuantization)
        .setupUndo(config.context[UndoManager], variableDescription = "Enable quantization")

    private val shiftGridToggle = CheckBox(config.shiftGrid)
        .setupUndo(config.context[UndoManager], variableDescription = "Shift grid")

    private val confirmAndStretchButton = Button("_Stretch") styleClass "sleek-button"

    private fun row(name: String, vararg items: Node) {
        addItem(name, HBox(5.0, *items).centerChildren())
    }

    override fun extraButtons(): List<Button> = listOf(confirmAndStretchButton)

    init {
        confirmAndStretchButton.setOnAction { commit(ResizeMode.Stretch) }
        val unresolvedGrid = config.meter.flatMap(ObjectReference<*>::isResolved).not().asObservableValue()
        shiftGridToggle.disableProperty().bind(unresolvedGrid)
        quantizationUnitInput.disableProperty().bind(unresolvedGrid)
        offsetUnitInput.disableProperty().bind(unresolvedGrid)
        quantizationUnitInput.label.minWidth = 50.0
        offsetUnitInput.label.minWidth = 50.0
        content.children.addAll(
            HBox(
                HBox(label("Enable: "), enableQuantizationToggle).alwaysHGrow().centerChildren(),
                HBox(label("Shift grid: "), shiftGridToggle).alwaysHGrow().centerChildren()
            ) styleClass "detail-item",
            HBox(
                5.0,
                HBox(label("Meter:  "), meterSelector).alwaysHGrow().centerChildren(),
                HBox(label("Clock: "), clockSelector).alwaysHGrow().centerChildren(),
            ) styleClass "detail-item",
        )
        row("Quant:", quantizationValueInput, quantizationUnitInput)
        row("Offset:", offsetValueInput, offsetUnitInput)
    }

    override fun confirm(): ResizeMode = ResizeMode.Regular

    companion object {
        private const val SELECTOR_WIDTH = 90.0
        private const val SPINNER_WIDTH = 120.0
    }
}