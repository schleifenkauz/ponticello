package ponticello.ui.scope

import fxutils.*
import fxutils.prompt.SimpleSelectorPrompt
import hextant.context.Context
import javafx.application.Platform
import javafx.scene.control.Label
import javafx.scene.control.RadioButton
import javafx.scene.control.TextField
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import ponticello.impl.*
import ponticello.sc.Transformation
import ponticello.sc.Warp
import ponticello.sc.WarpTransformation
import ponticello.sc.mapOnto
import ponticello.ui.impl.makeSubWindow
import reaktive.value.binding.equalTo
import reaktive.value.fx.asObservableValue
import reaktive.value.now

class LiveGraphPane(
    val context: Context,
    val target: LiveGraphs.Target, val id: Int
) : VBox() {
    private var window: SubWindow? = null

    private var customWarp: Warp = Warp.Linear
    private var customMin: Decimal = target.getSpec().now?.min?.get() ?: zero
    private var customMax: Decimal = target.getSpec().now?.max?.get() ?: one
    private var rangeOption: RangeOption =
        if (target.getSpec().now?.warp != null) RangeOption.Infer
        else RangeOption.Auto

    private val warpSelector = SimpleSelectorPrompt(Warp.entries, "Warp").selectorButton(this::customWarp)
    private val minField = textField(customMin.toString()) styleClass "sleek-text-field"
    private val maxField = textField(customMax.toString()) styleClass "sleek-text-field"

    private val intervalField = textField(LiveGraph.DEFAULT_INTERVAL.toString()) styleClass "sleek-text-field"
    private val lagField = textField(LiveGraph.DEFAULT_LAG.toString()) styleClass "sleek-text-field"
    private val rateField = textField(LiveGraph.DEFAULT_RATE.toString()) styleClass "sleek-text-field"

    private val toggleGroup = ToggleGroup()
    private val btnInfer = RadioButton("Infer")
    private val btnCustom = RadioButton("Custom")
    private val btnAuto = RadioButton("Auto")

    val liveGraph = LiveGraph(this::getTransformation, 10.0)

    init {
        setPrefSize(600.0, 400.0)
        val configBar = HBox(
            5.0,
            Label("Interval:"), intervalField,
            Label("Lag:"), lagField,
            Label("Rate:"), rateField,
            Label("Range:"),
            btnInfer, btnAuto, btnCustom,
            minField, Label(".."), maxField,
            Label("Warp:"), warpSelector
        ).centerChildren()
        for (btn in listOf(btnInfer, btnAuto, btnCustom)) {
            btn.toggleGroup = toggleGroup
        }
        btnInfer.disableProperty().bind(target.getSpec().equalTo(null).asObservableValue())
        if (rangeOption == RangeOption.Infer) toggleGroup.selectToggle(btnInfer)
        else toggleGroup.selectToggle(btnAuto)
        updatedRangeOption()
        toggleGroup.selectedToggleProperty().addListener { _, _, selected ->
            rangeOption = when (selected) {
                btnInfer -> RangeOption.Infer
                btnAuto -> RangeOption.Auto
                btnCustom -> RangeOption.Custom
                else -> throw IllegalStateException()
            }
            updatedRangeOption()
        }

        for (tf in listOf(minField, maxField, intervalField, lagField, rateField)) {
            tf.prefWidth = 60.0
        }
        minField.onCommit { value -> customMin = value.toDecimal() }
        maxField.onCommit { value -> customMax = value.toDecimal() }

        intervalField.onCommit { value -> liveGraph.setInterval(value) }
        lagField.onCommit { value -> context[LiveGraphs].setLag(id, value) }
        rateField.onCommit { value -> context[LiveGraphs].setRate(id, value) }

        children.add(configBar)

        children.add(liveGraph)
        liveGraph.widthProperty().bind(widthProperty())
        liveGraph.heightProperty().bind(heightProperty().subtract(configBar.heightProperty()))
    }

    private fun TextField.onCommit(
        range: DoubleRange = Double.MIN_VALUE..Double.MAX_VALUE, update: (Double) -> Unit
    ) {
        highlightWhenChanged()
        setOnAction {
            val value = this.text.toDoubleOrNull()?.takeIf { it in range } ?: return@setOnAction
            update(value)
            this.style = null
        }
    }

    private fun TextField.highlightWhenChanged() {
        textProperty().addListener { this.style = "-fx-text-fill: green" }
    }

    private fun updatedRangeOption() {
        warpSelector.isDisable = rangeOption == RangeOption.Infer
        minField.isDisable = rangeOption != RangeOption.Custom
        maxField.isDisable = rangeOption != RangeOption.Custom
        if (rangeOption == RangeOption.Infer) {
            val spec = target.getSpec().now ?: return
            customMin = spec.min.get()
            customMax = spec.max.get()
            customWarp = spec.warp
            minField.text = customMin.toString()
            maxField.text = customMax.toString()
        }
    }

    private fun getTransformation(): Transformation = when (rangeOption) {
        RangeOption.Infer -> target.getSpec().now?.mapOnto(liveGraph.targetRange) ?: automaticRange()
        RangeOption.Auto -> automaticRange()
        RangeOption.Custom -> WarpTransformation(customMin..customMax, customWarp, liveGraph.targetRange)
    }

    private fun automaticRange(): WarpTransformation {
        val sourceRange = liveGraph.currentValues.range()
        customMin = sourceRange.start
        customMax = sourceRange.endInclusive
        Platform.runLater {
            minField.text = customMin.toString()
            maxField.text = customMax.toString()
        }
        return WarpTransformation(sourceRange, customWarp, liveGraph.targetRange)
    }

    fun showWindow() {
        if (window == null) {
            val w = makeSubWindow(this, target.title, context)
            w.setOnCloseRequest { context[LiveGraphs].closedGraphPane(this) }
            window = w
        }
        window!!.showAndBringToFront()
    }

    private enum class RangeOption {
        Auto, Infer, Custom
    }
}