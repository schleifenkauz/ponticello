package ponticello.ui.controls

import fxutils.prompt.CompoundPrompt
import fxutils.prompt.SimpleSelectorPrompt
import fxutils.textField
import javafx.beans.binding.Bindings
import ponticello.impl.parseDecimal
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Warp

class SimpleNumericalControlSpecPrompt(
    initialSpec: NumericalControlSpec = NumericalControlSpec.DEFAULT
) : CompoundPrompt<NumericalControlSpec>("Edit spec") {
    private val minTxt = textField(initialSpec.min.text) named "Minimum"
    private val maxTxt = textField(initialSpec.max.text) named "Maximum"
    private val defaultTxt = textField(initialSpec.defaultValue.text) named "Default"
    private val stepTxt = textField(initialSpec.step.text) named "Step"

    private val min get() = minTxt.text.parseDecimal()
    private val max get() = maxTxt.text.parseDecimal()
    private val default get() = defaultTxt.text.parseDecimal()
    private val step get() = stepTxt.text.parseDecimal()

    private var warp = initialSpec.warp
    private val warpButton = SimpleSelectorPrompt(Warp.entries, "Choose warp")
        .selectorButton(this::warp) named "Warp"

    init {
        val isValid = Bindings.createBooleanBinding({
            when {
                min == null -> false
                max == null -> false
                default == null -> true
                step == null -> false
                else -> true
            }
        }, minTxt.textProperty(), maxTxt.textProperty(), defaultTxt.textProperty(), stepTxt.textProperty())
        confirmButton.disableProperty().bind(isValid.not())
    }

    override fun confirm(): NumericalControlSpec = NumericalControlSpec(default!!, min!!, max!!, step!!, warp)
}