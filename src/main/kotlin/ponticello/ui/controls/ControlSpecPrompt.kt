package ponticello.ui.controls

import fxutils.button
import fxutils.prompt.ConfirmablePrompt
import fxutils.prompt.Prompt
import javafx.beans.binding.BooleanBinding
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.control.Button
import ponticello.model.obj.ConfigurableInstrumentObject
import ponticello.model.obj.ParameterDefObject
import ponticello.model.obj.ParameterizedObject
import ponticello.sc.*
import reaktive.value.now

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
        val def = parentObject!!.def as ConfigurableInstrumentObject
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
        parentObject.def !is ConfigurableInstrumentObject -> emptyList()
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
        ): Prompt<out ControlSpec?, *>? {
            val title =
                if (parentObject != null) "Control spec for parameter $parameterName of ${parentObject.name.now}"
                else "Control spec for new parameter $parameterName"
            return when (initialSpec) {
                is BufferControlSpec -> BufferControlSpecPrompt(parameterName, parentObject, title, initialSpec)
                is BusControlSpec -> BusControlSpecPrompt(parameterName, parentObject, title, initialSpec)
                is NumericalControlSpec -> NumericalControlSpecPrompt(parameterName, parentObject, initialSpec, title)
                is AttackReleaseControlSpec -> AttackReleaseControlSpecPrompt(
                    initialSpec, "Maximum attack/release"
                )

                is BufferPositionControlSpec, is ExprControlSpec -> null
            }
        }

        fun createParameter(
            parameterName: String,
            parentObject: ParameterizedObject?,
            parameterType: ParameterType,
            ownerWindow: javafx.stage.Window? = null, anchor: Point2D?,
        ): ControlSpec? = when (parameterType) {
            ParameterType.Bus ->
                create(parameterName, parentObject, BusControlSpec(Rate.Audio, 2))?.showDialog(ownerWindow, anchor)

            ParameterType.Buffer ->
                create(parameterName, parentObject, BufferControlSpec(2))?.showDialog(ownerWindow, anchor)

            ParameterType.Numerical ->
                create(parameterName, parentObject, NumericalControlSpec.DEFAULT)?.showDialog(ownerWindow, anchor)

            ParameterType.AttackRelease ->
                create(parameterName, parentObject, AttackReleaseControlSpec())?.showDialog(ownerWindow, anchor)

            ParameterType.BufferPosition -> BufferPositionControlSpec()
            ParameterType.Expr -> ExprControlSpec()
        }
    }
}