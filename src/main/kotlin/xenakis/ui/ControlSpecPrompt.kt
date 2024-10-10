package xenakis.ui

import javafx.beans.binding.Bindings
import javafx.collections.FXCollections.observableList
import javafx.scene.control.Button
import javafx.scene.control.ColorPicker
import javafx.scene.control.ComboBox
import javafx.scene.control.ToggleButton
import javafx.scene.paint.Color
import org.controlsfx.control.SegmentedButton
import reaktive.value.now
import xenakis.impl.parseDecimal
import xenakis.model.CustomizableSynthDefObject
import xenakis.model.ParameterDefObject
import xenakis.model.SynthObject
import xenakis.sc.*
import xenakis.sc.ParameterType.*
import xenakis.ui.prompt.CompoundPrompt

class ControlSpecPrompt(
    private val obj: SynthObject, private val parameter: String,
    initialSpec: ControlSpec
) : CompoundPrompt<ParameterDefObject>("Control spec for parameter $parameter of synth ${obj.name.now}") {
    private var currentSpecType = initialSpec.type
        set(value) {
            if (field == value) return
            field = value
            content.clear()
            specTypeButtonBar named "Parameter type"
            if (value == Numerical) addNumericalSpecInputs()
        }
    private val numericalSpec = initialSpec as? NumericalControlSpec
    private val specTypeButtonBar = SegmentedButton(*values().map { type ->
        val btn = ToggleButton(type.name)
        if (type == currentSpecType) btn.isSelected = true
        btn.selectedProperty().addListener { _, _, selected ->
            if (selected) currentSpecType = type
        }
        btn
    }.toTypedArray())

    private val resetBtn = button("Reset") {
        reset()
        window.hide()
    }

    private val confirmAndSyncBtn = button("Confirm and sync") {
        confirmAndSync()
        window.hide()
    }

    private val confirmAndAddBtn = button("Confirm and add to SynthDef") {
        confirmAndAdd()
        window.hide()
    }

    private val minTxt = textField(numericalSpec?.min?.text ?: "0")
    private val maxTxt = textField(numericalSpec?.max?.text ?: "1")
    private val defaultTxt = textField(numericalSpec?.defaultValue?.text ?: "0")
    private val stepTxt = textField(numericalSpec?.step?.text ?: "0.1")
    private val warpBox = ComboBox(observableList(Warp.values().asList()))
    private val associatedColor = ColorPicker(numericalSpec?.associatedColor ?: Color.BLACK)

    private val min get() = minTxt.text.parseDecimal()
    private val max get() = maxTxt.text.parseDecimal()
    private val default get() = defaultTxt.text.parseDecimal()
    private val step get() = stepTxt.text.parseDecimal()
    private val warp get() = warpBox.value

    init {
        warpBox.value = numericalSpec?.warp ?: Warp.Linear
        if (parameter !in obj.controls.controlMap) specTypeButtonBar named "Parameter type"
        if (currentSpecType == Numerical) addNumericalSpecInputs()
        val isValid = Bindings.createBooleanBinding({
            when {
                currentSpecType != Numerical -> true
                min == null -> false
                max == null -> false
                default == null -> true
                step == null -> false
                default!! < min!! -> false
                default!! > max!! -> false
                else -> true
            }
        }, minTxt.textProperty(), maxTxt.textProperty(), defaultTxt.textProperty(), stepTxt.textProperty())
        confirmButton.disableProperty().bind(isValid.not())
        confirmAndSyncBtn.disableProperty().bind(isValid.not())
    }

    private fun addNumericalSpecInputs() {
        minTxt named "Minimum"
        maxTxt named "Maximum"
        defaultTxt named "Default"
        stepTxt named "Step"
        warpBox named "Warp"
        associatedColor named "Color"
    }

    override fun extraButtons(): List<Button> = when {
        obj.synthDef !is CustomizableSynthDefObject -> emptyList()
        obj.synthDef.hasParameter(parameter) -> listOf(confirmAndSyncBtn, resetBtn)
        else -> listOf(confirmAndAddBtn)
    }

    private fun reset() {
        obj.controls.removeExtraSpec(parameter)
    }

    private fun confirmAndSync() {
        val param = confirm()
        val defParameter = obj.synthDef.getParameter(parameter)!!
        defParameter.spec.now = param.spec.now
    }

    private fun confirmAndAdd() {
        val param = confirm()
        val def = obj.synthDef as CustomizableSynthDefObject
        def.parameters.now.add(param)
    }

    override fun confirm(): ParameterDefObject {
        val spec = makeSpec()
        if (parameter in obj.controls.controlMap) {
            obj.controls.setExtraSpec(parameter, spec)
        }
        return ParameterDefObject(parameter, spec)
    }

    private fun makeSpec() = when (currentSpecType) {
        Numerical -> NumericalControlSpec(default!!, min!!, max!!, step!!, warp, associatedColor.value)
        Bus -> BusControlSpec()
        Buffer -> BufferControlSpec()
        Group -> GroupControlSpec()
    }
}