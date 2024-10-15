package xenakis.ui.controls

import hextant.fx.registerShortcuts
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
    private val value: ReactiveVariable<Decimal>,
) : HBox(5.0) {
    private val spec get() = obj.getSpec(parameter) as NumericalControlSpec

    private val slider = Slider()
    private var buttonReleased = false
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
            on("Ctrl+BACK_SPACE") { value.now = spec.defaultValue.get() }
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
        if (value.now + delta in spec.range) value.now += delta
    }

    private fun setupDataFlow() {
        var updating = false
        valueObserver = value.forEach { v ->
            textInput.text = v.toString()
            if (updating) return@forEach
            val mapped = transform.map(v.toDouble())
            updating = true
            slider.value = mapped
            updating = false
        }
        slider.valueProperty().addListener { _, _, v ->
            if (updating) return@addListener
            val unmapped = transform.unmap(v.toDouble()).snap(spec.step.get())
            updating = true
            value.set(unmapped)
            updating = false
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
            value.now = v
        }
        textInput.focusedProperty().addListener { _, _, focused ->
            if (!focused) textInput.text = value.now.toString()
        }
    }

}