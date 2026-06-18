package ponticello.ui.controls

import fxutils.prompt.ConfirmablePrompt
import fxutils.prompt.Prompt
import fxutils.prompt.PromptPlacement
import fxutils.styleClass
import javafx.beans.binding.BooleanBinding
import javafx.scene.Node
import javafx.scene.control.Button
import ponticello.model.instr.ConfigurableInstrumentObject
import ponticello.model.instr.ParameterDefObject
import ponticello.model.instr.ParameterizedObject
import ponticello.sc.*
import reaktive.value.now

abstract class ControlSpecPrompt<S : ControlSpec, N : Node>(
    private val parameterName: String,
    protected val parentObject: ParameterizedObject?,
    title: String,
) : ConfirmablePrompt<S>(title) {
    private val resetBtn = Button("_Reset") styleClass "sleek-button"

    private val confirmAndSyncBtn = Button("_Sync") styleClass "sleek-button"

    private val confirmAndAddBtn = Button("_Add to SynthDef") styleClass "sleek-button"

    init {
        resetBtn.setOnAction { reset() }
        confirmAndSyncBtn.setOnAction { confirmAndSync() }
        confirmAndAddBtn.setOnAction { confirmAndAdd() }
    }

    private fun reset() {
        parentObject!!.controls.get(parameterName).setCustomSpec(null)
        commit(null)
    }

    private fun confirmAndSync() {
        val spec = confirm()
        parentObject!!.getInstrument().setSpec(parameterName, spec)
        parentObject.controls.get(parameterName).setCustomSpec(null)
        commit(spec)
    }

    private fun confirmAndAdd() {
        val spec = confirm()
        val param = ParameterDefObject(parameterName, spec)
        val def = parentObject!!.getInstrument() as ConfigurableInstrumentObject
        def.parameters.add(param)
        commit(spec)
    }

    protected fun validate(isValid: BooleanBinding) {
        confirmButton.disableProperty().bind(isValid.not())
        confirmAndSyncBtn.disableProperty().bind(isValid.not())
        confirmAndAddBtn.disableProperty().bind(isValid.not())
    }

    protected abstract fun makeSpec(): S

    override fun extraButtons(): List<Button> = when {
        parentObject == null -> emptyList()
        parentObject.getInstrument() !is ConfigurableInstrumentObject -> emptyList()
        parentObject.getInstrument().hasParameter(parameterName) -> listOf(confirmAndSyncBtn, resetBtn)
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
        ): Prompt<out ControlSpec?>? {
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

                is BufferPositionControlSpec, is ExprControlSpec, is ScoreObjectControlSpec -> null
            }
        }

        fun createParameter(
            parameterName: String,
            parentObject: ParameterizedObject?,
            parameterType: ParameterType,
            promptPlacement: PromptPlacement,
        ): ControlSpec? = when (parameterType) {
            ParameterType.Bus ->
                create(parameterName, parentObject, BusControlSpec(Rate.Audio, 2))?.showDialog(promptPlacement)

            ParameterType.Buffer ->
                create(parameterName, parentObject, BufferControlSpec(2))?.showDialog(promptPlacement)

            ParameterType.Numerical ->
                create(parameterName, parentObject, NumericalControlSpec.DEFAULT)?.showDialog(promptPlacement)

            ParameterType.AttackRelease ->
                create(parameterName, parentObject, AttackReleaseControlSpec())?.showDialog(promptPlacement)

            ParameterType.BufferPosition -> BufferPositionControlSpec()
            ParameterType.Expr -> ExprControlSpec()
            ParameterType.ScoreObject -> ScoreObjectControlSpec()
            ParameterType.Trig -> NumericalControlSpec.TRIGGER
        }
    }
}