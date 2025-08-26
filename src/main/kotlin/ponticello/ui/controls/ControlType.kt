package ponticello.ui.controls

import fxutils.actions.ContextualizedAction
import hextant.context.Context
import javafx.scene.Node
import javafx.scene.layout.Region
import ponticello.model.obj.ParameterizedObject
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.*
import ponticello.sc.ControlSpec
import ponticello.ui.score.ScoreObjectView

sealed class ControlType<C : ParameterControl> {
    open fun applicableOn(obj: ParameterizedObject, spec: ControlSpec): Boolean = true

    abstract fun createInitialControl(
        obj: ParameterizedObject, spec: ControlSpec?, oldControl: ParameterControl?,
        parameterName: String, anchorNode: Region?,
    ): C

    abstract fun createDetailInput(namedControl: NamedParameterControl, control: C, view: ScoreObjectView?): Node

    open fun onSelected(namedControl: NamedParameterControl, control: C, view: ScoreObjectView?) {}

    open fun createSimpleInput(namedControl: NamedParameterControl, control: C): Node? = null

    open fun supportsDialogInput(): Boolean = false

    open fun showDialogInput(
        parameterName: String, specs: List<ControlSpec>, controls: List<C>, context: Context,
    ): Boolean {
        throw AssertionError("Dialog input not supported for control type $this")
    }

    open fun actions(
        namedControl: NamedParameterControl, control: C, view: ScoreObjectView?,
    ): List<ContextualizedAction> = emptyList()


    companion object {
        val all: List<ControlType<*>> = listOf(
            BusControlType,
            BufferControlType,
            ValueControlType,
            EnvelopeControlType,
            AttackReleaseControlType,
            BusValueControlType,
            ExprControlType,
            UGenControlType
        )

        @Suppress("UNCHECKED_CAST")
        fun <O : ParameterControl> getType(option: O) = when (option) {
            is ValueControl -> ValueControlType
            is UGenControl -> UGenControlType
            is EnvelopeControl -> EnvelopeControlType
            is BusControl -> BusControlType
            is BusValueControl -> BusValueControlType
            is BufferControl -> BufferControlType
            is ExprControl -> ExprControlType
            is AttackReleaseControl -> AttackReleaseControlType
            else -> throw AssertionError()
        } as ControlType<O>
    }
}
