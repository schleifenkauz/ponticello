package xenakis.ui.controls

import hextant.fx.registerShortcuts
import hextant.undo.AbstractEdit
import hextant.undo.Edit
import hextant.undo.UndoManager
import javafx.application.Platform
import javafx.scene.control.Slider
import javafx.scene.control.TextField
import javafx.scene.input.KeyEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.forEach
import reaktive.value.now
import xenakis.impl.Decimal
import xenakis.impl.parseDecimal
import xenakis.impl.snap
import xenakis.impl.unaryMinus
import xenakis.model.score.ParameterizedScoreObject
import xenakis.sc.DecimalLiteral
import xenakis.sc.NumericalControlSpec
import xenakis.sc.SpecTransformation
import xenakis.ui.Icon
import xenakis.ui.impl.centerChildren
import xenakis.ui.impl.styleClass
import xenakis.ui.prompt.YesNoPrompt
import kotlin.concurrent.thread

class ControlSlider(
    private val obj: ParameterizedScoreObject,
    private val parameter: String,
    private val variable: ReactiveVariable<Decimal>,
) : HBox(5.0) {
    private val spec get() = obj.getSpec(parameter) as NumericalControlSpec

    private val slider = Slider()
    private var buttonReleased = false
    private var updating = false
    private val textInput = TextField().styleClass("control-input-text")
    private val btnDec = Icon.Minus.button(radius = 12.0, action = "Decrement").styleClass("slider-adjust-button")
    private val btnInc = Icon.Add.button(radius = 12.0, action = "Increment").styleClass("slider-adjust-button")
    private lateinit var valueObserver: Observer
    private val transform = SpecTransformation(spec, 0.0..1.0)

    override fun requestFocus() {
        textInput.requestFocus()
    }

    init {
        styleClass("control-slider")
        slider.min = 0.0
        slider.max = 1.0
        btnInc.setOnMousePressed { startAdjust(spec.step.get()) }
        btnInc.setOnMouseReleased { buttonReleased = true }
        btnDec.setOnMousePressed { startAdjust(-spec.step.get()) }
        btnDec.setOnMouseReleased { buttonReleased = true }
        textInput.registerShortcuts(eventType = KeyEvent.KEY_PRESSED) {
            on("Alt+PLUS") { adjust(spec.step.get()) }
            on("UP") { adjust(spec.step.get()) }
            on("Alt+MINUS") { adjust(-spec.step.get()) }
            on("DOWN") { adjust(-spec.step.get()) }
            on("Ctrl+BACK_SPACE") { variable.now = spec.defaultValue.get() }
        }
        children.addAll(textInput, /*btnDec, */slider /*btnInc*/)
        setHgrow(slider, Priority.ALWAYS)
        centerChildren()
        setupDataFlow()
    }

    private fun startAdjust(delta: Decimal) {
        buttonReleased = false
        thread {
            while (!buttonReleased) {
                Thread.sleep(100)
                Platform.runLater { adjust(delta) }
            }
        }
    }

    private fun adjust(delta: Decimal) {
        if (variable.now + delta in spec.range) variable.now += delta
    }

    private fun setupDataFlow() {
        valueObserver = variable.forEach { v ->
            textInput.text = v.toString()
            if (updating) return@forEach
            val mapped = transform.map(v.toDouble())
            slider.value = mapped
        }
        slider.valueChangingProperty().addListener { _, _, changing ->
            if (changing) obj.context[UndoManager].beginCompoundEdit("Adjust $parameter")
            else obj.context[UndoManager].finishCompoundEdit("Adjust $parameter")
        }
        slider.valueProperty().addListener { _, _, v ->
            if (updating) return@addListener
            val newValue = transform.unmap(v.toDouble()).snap(spec.step.get())
            updateValue(newValue)
        }
        textInput.setOnAction {
            var v = textInput.text.parseDecimal() ?: return@setOnAction
            v = v.value.snap(spec.step.get())
            if (v !in spec.range) {
                val extendRange = YesNoPrompt(
                    "Value is outside of range ${spec.range}. Do you want to extend the range of this parameter?"
                ).showDialog(obj.context)
                if (extendRange == true) {
                    val newSpec =
                        if (v < spec.min.get()) spec.copy(min = DecimalLiteral(v))
                        else spec.copy(max = DecimalLiteral(v))
                    obj.controls.setExtraSpec(parameter, newSpec)
                } else {
                    v = v.coerceIn(spec.range)
                }
            }
            v = v.coerceIn(spec.range)
            updateValue(v)
        }
        textInput.focusedProperty().addListener { _, _, focused ->
            if (!focused) textInput.text = variable.now.toString()
        }
    }

    private fun updateValue(newValue: Decimal) {
        updating = true
        val oldValue = variable.get()
        variable.set(newValue)
        obj.context[UndoManager].record(UpdateValue(variable, parameter, oldValue, newValue))
        updating = false
    }

    private class UpdateValue(
        private val variable: ReactiveVariable<Decimal>, private val parameter: String,
        private val oldValue: Decimal, private val newValue: Decimal
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Update $parameter"

        override fun doRedo() {
            variable.set(newValue)
        }

        override fun doUndo() {
            variable.set(oldValue)
        }

        override fun mergeWith(other: Edit): Edit? = when {
            other !is UpdateValue -> null
            other.parameter != this.parameter -> null
            other.variable !== this.variable -> null
            other.oldValue != this.newValue -> null
            else -> UpdateValue(variable, parameter, this.oldValue, other.newValue)
        }
    }
}