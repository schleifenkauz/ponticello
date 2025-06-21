package ponticello.ui.controls

import fxutils.controls.CheckBox
import fxutils.prompt.DetailPane
import fxutils.prompt.SimpleSearchableListView
import fxutils.textField
import javafx.beans.binding.Bindings
import javafx.scene.Node
import javafx.scene.control.ColorPicker
import ponticello.impl.parseDecimal
import ponticello.model.obj.ParameterizedObject
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Warp

class NumericalControlSpecPrompt(
    parameterName: String, parentObject: ParameterizedObject?, initialSpec: NumericalControlSpec, title: String,
) : ControlSpecPrompt<NumericalControlSpec, DetailPane>(parameterName, parentObject, title) {
    override val content: DetailPane = DetailPane(labelWidth = 120.0)
    private val minTxt = textField(initialSpec.min.text) named "Minimum"
    private val maxTxt = textField(initialSpec.max.text) named "Maximum"
    private val defaultTxt = textField(initialSpec.defaultValue.text) named "Default"
    private val stepTxt = textField(initialSpec.step.text) named "Step"
    private val lagTxt = textField(initialSpec.lag.text) named "Lag"
    private val associatedColor = ColorPicker(initialSpec.associatedColor) named "Color"
    private val inlineDisplayBox = CheckBox(initialSpec.inlineDisplay) named "Inline display"

    private val min get() = minTxt.text.parseDecimal()
    private val max get() = maxTxt.text.parseDecimal()
    private val default get() = defaultTxt.text.parseDecimal()
    private val step get() = stepTxt.text.parseDecimal()
    private val lag get() = lagTxt.text.parseDecimal()
    private var warp = initialSpec.warp
    private val warpButton = SimpleSearchableListView(Warp.entries, "Choose warp")
        .selectorButton(this::warp) named "Warp"

    init {
        val isValid = Bindings.createBooleanBinding({
            when {
                min == null -> false
                max == null -> false
                default == null -> true
                step == null -> false
                lag == null -> false
                default!! < min!! -> false
                default!! > max!! -> false
                else -> true
            }
        }, minTxt.textProperty(), maxTxt.textProperty(), defaultTxt.textProperty(), stepTxt.textProperty())
        validate(isValid)
    }

    override fun onReceiveFocus() {
        minTxt.requestFocus()
    }

    private infix fun <N : Node> N.named(name: String): N = also { content.addItem(name, it) }

    override fun makeSpec() = NumericalControlSpec(
        default!!, min!!, max!!, step!!, lag!!, warp,
        associatedColor.value, inlineDisplayBox.isSelected
    )
}