package ponticello.ui.live

import fxutils.*
import fxutils.controls.CheckBox
import fxutils.controls.OptionSpinner
import fxutils.prompt.CompoundPrompt
import fxutils.prompt.SimpleSearchableListView
import fxutils.undo.UndoManager
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Spinner
import javafx.scene.layout.HBox
import ponticello.impl.sync
import ponticello.model.live.QuantizationConfig
import ponticello.model.live.QuantizationUnit
import ponticello.model.registry.ClockRegistry
import ponticello.model.registry.MeterRegistry
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.model.score.ScoreObject.ResizeMode
import ponticello.model.score.TimeUnit
import reaktive.value.binding.flatMap
import reaktive.value.binding.not
import reaktive.value.fx.asObservableValue
import reaktive.value.now

class QuantizationConfigDialog(
    config: QuantizationConfig, title: String,
) : CompoundPrompt<ResizeMode>(title, labelWidth = 100.0) {
    private val meters = config.context[MeterRegistry].map { obj -> obj.reference() }
    private val clocks = config.context[ClockRegistry].map { obj -> obj.reference() }

    private val meterSelector = SimpleSearchableListView(meters, "Choose meter")
        .selectorButton(config.meter)
        .setFixedWidth(SELECTOR_WIDTH)

    private val clockSelector = SimpleSearchableListView(clocks, "Choose clock")
        .selectorButton(config.clock)
        .setFixedWidth(SELECTOR_WIDTH)

    private val quantizationUnitInput = OptionSpinner(config.quantizationUnit, QuantizationUnit.entries)

    private val quantizationValueInput = Spinner<Double>(0.0, Double.MAX_VALUE, config.quantizationValue.now.value)
        .sync(config.quantizationValue)
        .setFixedWidth(SPINNER_WIDTH)

    private val offsetUnitInput = OptionSpinner(config.offsetUnit, TimeUnit.entries)

    private val offsetValueInput = Spinner<Double>(0.0, Double.MAX_VALUE, config.offsetValue.now.value)
        .sync(config.offsetValue)
        .setFixedWidth(SPINNER_WIDTH)

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
                label("Enable:"), enableQuantizationToggle, hspace(5.0),
                label("Shift grid:"), shiftGridToggle
            ) styleClass "detail-item",
            HBox(
                label("Meter:"), meterSelector, hspace(5.0),
                label("Clock:"), clockSelector
            ) styleClass "detail-item",
        )
        row("Quantization", quantizationValueInput, quantizationUnitInput)
        row("Offset", offsetValueInput, offsetUnitInput)
    }

    override fun confirm(): ResizeMode = ResizeMode.Regular

    companion object {
        private const val SELECTOR_WIDTH = 80.0
        private const val SPINNER_WIDTH = 100.0
    }
}