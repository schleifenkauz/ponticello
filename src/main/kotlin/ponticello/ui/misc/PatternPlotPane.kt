package ponticello.ui.misc

import fxutils.centerChildren
import fxutils.infiniteSpace
import fxutils.prompt.SimpleSelectorPrompt
import javafx.application.Platform
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.control.OverrunStyle
import javafx.scene.control.TextField
import javafx.scene.control.Tooltip
import javafx.scene.layout.HBox
import ponticello.impl.writeCode
import ponticello.model.code.GlobalPatternObject
import ponticello.sc.Warp
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import ponticello.ui.dock.ToolPane
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.forEach
import reaktive.value.now
import reaktive.value.reactiveValue

class PatternPlotPane(private val pattern: GlobalPatternObject) : ToolPane() {
    override val title: ReactiveValue<String>
        get() = reactiveValue("Pattern Plot")

    private val plot = PatternPlot()
    private val warpSelector = SimpleSelectorPrompt(Warp.entries, "Select warp")
    private val sampleNumber = TextField("20").apply { prefWidth = 50.0 }
    private val errorDisplay = Label().apply {
        style = "-fx-text-fill: red; -fx-font-weight: bold;"
        maxWidth = 200.0
        textOverrun = OverrunStyle.ELLIPSIS
    }

    private lateinit var codeObserver: Observer

    override val content: Parent get() = plot
    override val headerContent = createHeader()

    init {
        setup()
        setPrefSize(500.0, 500.0)
    }

    override fun doSetup() {
        codeObserver = pattern.patternCode.editor.result.forEach {
            displayPattern()
        }
        sampleNumber.setOnAction {
            displayPattern()
        }
    }

    private fun createHeader(): HBox {
        val warpButton = warpSelector.selectorButton(plot::warp)
        val headerContent = HBox(
            5.0,
            Label("Number of samples: "), sampleNumber,
            warpButton,
            plot.valueLabel,
            infiniteSpace(),
            errorDisplay
        ).centerChildren()
        return headerContent
    }

    private fun displayError(error: String) = Platform.runLater {
        errorDisplay.text = error
        errorDisplay.tooltip = Tooltip(error)
    }

    private fun update(result: String) {
        val values = result.removePrefix("[").removeSuffix("]").split(", ").map { v ->
            v.toDoubleOrNull() ?: return displayError("Some values could not be parsed as numbers")
        }
        Platform.runLater {
            plot.update(values)
            errorDisplay.text = ""
        }
    }

    private fun displayPattern() {
        val expr = pattern.patternCode.editor.result.now
        if (!expr.isValid) return displayError("Invalid pattern code")
        val n = sampleNumber.text.toIntOrNull() ?: return displayError("Invalid sample number")
        val client = pattern.context[SuperColliderClient]
        val code = writeCode {
            expr.code(writer, pattern.context)
            append(".asStream.nextN($n).asCompileString")
        }
        client.eval(code, description = "Plot pattern ${pattern.name.now}", onError = ::displayError, onSuccess = ::update)
    }
}