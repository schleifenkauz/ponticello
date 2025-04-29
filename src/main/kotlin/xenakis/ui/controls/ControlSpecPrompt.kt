package xenakis.ui.controls

import fxutils.button
import fxutils.prompt.ConfirmablePrompt
import javafx.beans.binding.BooleanBinding
import javafx.scene.Node
import javafx.scene.control.Button
import reaktive.value.now
import xenakis.model.obj.ConfigurableParameterizedObjectDef
import xenakis.model.obj.ParameterDefObject
import xenakis.model.obj.ParameterizedObject
import xenakis.sc.*

abstract class ControlSpecPrompt<S : ControlSpec, N : Node>(
    private val parameterName: String,
    protected val parentObject: ParameterizedObject?,
    title: String,
) : ConfirmablePrompt<S, N>(title) {
    private val resetBtn = button("Reset") {
        reset()
    }

    private val confirmAndSyncBtn = button("Confirm and sync") {
        confirmAndSync()
    }

    private val confirmAndAddBtn = button("Confirm and add to SynthDef") {
        confirmAndAdd()
    }

    private fun reset() {
        parentObject!!.controls.get(parameterName).setCustomSpec(null)
        commit(null)
    }

    private fun confirmAndSync() {
        val spec = confirm()
        parentObject!!.def.setSpec(parameterName, spec)
        parentObject.controls.get(parameterName).setCustomSpec(null)
        commit(spec)
    }

    private fun confirmAndAdd() {
        val spec = confirm()
        val param = ParameterDefObject(parameterName, spec)
        val def = parentObject!!.def as ConfigurableParameterizedObjectDef
        def.parameters.add(param)
        commit(spec)
    }

    protected fun validate(isValid: BooleanBinding) {
        confirmButton.disableProperty().bind(isValid.not())
        confirmAndSyncBtn.disableProperty().bind(isValid.not())
    }

    protected abstract fun makeSpec(): S

    override fun extraButtons(): List<Button> = when {
        parentObject == null -> emptyList()
        parentObject.def !is ConfigurableParameterizedObjectDef -> emptyList()
        parentObject.def.hasParameter(parameterName) -> listOf(confirmAndSyncBtn, resetBtn)
        else -> listOf(confirmAndAddBtn)
    }

    override fun confirm(): S {
        val spec = makeSpec()
        if (parentObject != null && parameterName in parentObject.controls.controlMap) {
            parentObject.controls.get(parameterName).setCustomSpec(spec)
        }
        return spec
    }

    companion object {
        fun create(
            parameterName: String,
            parentObject: ParameterizedObject?,
            initialSpec: ControlSpec,
        ): ControlSpecPrompt<*, *>? {
            val title =
                if (parentObject != null) "Control spec for parameter $parameterName of ${parentObject.name.now}"
                else "Control spec for new parameter $parameterName"
            return when (initialSpec) {
                is BufferControlSpec -> BufferControlSpecPrompt(parameterName, parentObject, title, initialSpec)
                is BusControlSpec -> BusControlSpecPrompt(parameterName, parentObject, title, initialSpec)
                is NumericalControlSpec -> NumericalControlSpecPrompt(parameterName, parentObject, initialSpec, title)
                is BufferPositionControlSpec -> null
            }
        }

        fun create(
            parameterName: String,
            parentObject: ParameterizedObject?,
            parameterType: ParameterType,
        ): ControlSpecPrompt<*, *>? = when (parameterType) {
            ParameterType.Bus -> create(parameterName, parentObject, BusControlSpec(Rate.Audio, 2))
            ParameterType.Buffer -> create(parameterName, parentObject, BufferControlSpec(2))
            ParameterType.Numerical -> create(parameterName, parentObject, NumericalControlSpec.DEFAULT)
            ParameterType.BufferPosition -> null
        }
    }
}