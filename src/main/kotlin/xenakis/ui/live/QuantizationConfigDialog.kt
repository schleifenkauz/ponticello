package xenakis.ui.live

import fxutils.*
import fxutils.prompt.CompoundPrompt
import fxutils.prompt.SimpleSearchableListView
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.Spinner
import javafx.scene.layout.HBox
import org.controlsfx.control.ToggleSwitch
import reaktive.value.binding.flatMap
import reaktive.value.binding.not
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.impl.sync
import xenakis.model.live.QuantizationConfig
import xenakis.model.live.QuantizationUnit
import xenakis.model.registry.MeterRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.reference
import xenakis.model.score.ScoreObject.ResizeMode
import xenakis.model.score.TimeUnit

class QuantizationConfigDialog(
    private val config: QuantizationConfig, title: String,
) : CompoundPrompt<ResizeMode>(title, labelWidth = 150.0) {
    private val tempoGrids = config.context[MeterRegistry].map { obj -> obj.reference() }

    private val gridSelector = SimpleSearchableListView(tempoGrids, "Choose grid")
        .selectorButton(config.meter)
        .setFixedWidth(SELECTOR_WIDTH)

    private val durationUnitInput = SimpleSearchableListView(TimeUnit.entries, "Choose duration unit")
        .selectorButton(config.durationUnit)
        .setFixedWidth(SELECTOR_WIDTH)

    private val durationValueInput = Spinner<Double>(1.0, Double.MAX_VALUE, config.durationValue.now.value)
        .sync(config.durationValue)
        .setFixedWidth(SPINNER_WIDTH)

    private val quantizationUnitInput = SimpleSearchableListView(QuantizationUnit.entries, "Choose quantization unit")
        .selectorButton(config.quantizationUnit)
        .setFixedWidth(SELECTOR_WIDTH)

    private val quantizationValueInput = Spinner<Int>(1, Int.MAX_VALUE, config.quantizationValue.now)
        .sync(config.quantizationValue)
        .setFixedWidth(SPINNER_WIDTH)

    private val offsetUnitInput = SimpleSearchableListView(TimeUnit.entries, "Choose offset unit")
        .selectorButton(config.offsetUnit)
        .setFixedWidth(SELECTOR_WIDTH)

    private val offsetValueInput = Spinner<Double>(0.0, Double.MAX_VALUE, config.offsetValue.now.value)
        .sync(config.offsetValue)
        .setFixedWidth(SPINNER_WIDTH)

    private val enableQuantizationToggle = ToggleSwitch()
        .sync(config.enableQuantization)
    private val shiftGridToggle = ToggleSwitch()
        .sync(config.shiftGrid)

    private fun row(name: String, vararg items: Node) {
        addItem(name, HBox(5.0, *items).centerChildren())
    }

    override fun extraButtons(): List<Button> = listOf(
        button("Confirm and stretch") {
            commit(ResizeMode.Stretch)
        }
    )

    init {
        val unresolvedGrid = config.meter.flatMap(ObjectReference<*>::isResolved).not().asObservableValue()
        shiftGridToggle.disableProperty().bind(unresolvedGrid)
        durationUnitInput.disableProperty().bind(unresolvedGrid)
        quantizationUnitInput.disableProperty().bind(unresolvedGrid)
        offsetUnitInput.disableProperty().bind(unresolvedGrid)
        row("Reference grid", gridSelector, hspace(SPINNER_WIDTH))
        row("Duration: ", durationUnitInput, durationValueInput)
        row("Quantization: ", quantizationUnitInput, quantizationValueInput)
        row("Offset: ", offsetUnitInput, offsetValueInput)
        addItem("Enable quantization", enableQuantizationToggle)
        addItem("Shift grid", shiftGridToggle)
    }

    override fun confirm(): ResizeMode = ResizeMode.Regular

    companion object {
        private const val SELECTOR_WIDTH = 120.0
        private const val SPINNER_WIDTH = 80.0
    }
}