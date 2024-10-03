package xenakis.ui

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
import xenakis.sc.NumericalControlSpec
import xenakis.sc.SpecTransformation
import kotlin.concurrent.thread

class ControlSlider(
    private val value: ReactiveVariable<Double>,
    private val spec: NumericalControlSpec,
) : HBox(5.0) {
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
        btnInc.setOnMousePressed { startAdjust(+spec.step.get()) }
        btnInc.setOnMouseReleased { buttonReleased = true }
        btnDec.setOnMousePressed { startAdjust(-spec.step.get()) }
        btnDec.setOnMouseReleased { buttonReleased = true }
        textInput.registerShortcuts(eventType = KeyEvent.KEY_PRESSED) {
            on("Alt+PLUS") { adjust(+spec.step.get()) }
            on("UP") { adjust(+spec.step.get()) }
            on("Alt+MINUS") { adjust(-spec.step.get()) }
            on("DOWN") { adjust(-spec.step.get()) }
            on("Ctrl+BACK_SPACE") { value.now = spec.defaultValue.get() }
        }
        children.addAll(textInput, /*btnDec, */slider /*btnInc*/)
        setHgrow(slider, Priority.ALWAYS)
        centerChildren()
        setupDataFlow()
    }

    private fun startAdjust(delta: Double) {
        buttonReleased = false
        thread {
            while (!buttonReleased) {
                Thread.sleep(100)
                Platform.runLater { adjust(delta) }
            }
        }
    }

    private fun adjust(delta: Double) {
        if (value.now + delta in spec.range) value.now += delta
    }

    private fun setupDataFlow() {
        var updating = false
        valueObserver = value.forEach { v ->
            textInput.text = v.format(accuracy(spec.step.get()))
            if (updating) return@forEach
            val mapped = transform.map(v)
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
            var v = textInput.text.toDoubleOrNull() ?: return@setOnAction
            v = v.coerceIn(spec.range)
            v = v.snap(spec.step.get())
            value.now = v
        }
        textInput.focusedProperty().addListener { _, _, focused ->
            if (!focused) textInput.text = value.now.format(accuracy(spec.step.get()))
        }
    }

}