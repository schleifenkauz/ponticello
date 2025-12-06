package ponticello.ui.controls

import fxutils.actions.ContextualizedAction
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import hextant.context.Context
import hextant.context.compoundEdit
import javafx.event.Event
import javafx.scene.Node
import ponticello.impl.Logger
import ponticello.model.instr.ParameterizedObject
import ponticello.model.registry.reference
import ponticello.model.score.controls.BusControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ParameterControlList
import ponticello.model.score.controls.getControlBus
import ponticello.model.server.BusRegistry
import ponticello.sc.BusControlSpec
import ponticello.sc.ControlSpec
import ponticello.ui.actions.ServerActions
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.registry.BusSelectorPrompt
import ponticello.ui.score.ScoreObjectView
import reaktive.value.now
import reaktive.value.reactiveVariable

data object BusControlType : ControlType<BusControl>() {
    override fun applicableOn(obj: ParameterizedObject, spec: ControlSpec): Boolean = spec is BusControlSpec

    override fun createDetailInput(
        namedControl: ParameterControlList.NamedParameterControl,
        control: BusControl,
        view: ScoreObjectView?,
    ): Node = busSelector(control.bus, namedControl.spec.now, namedControl.context)

    override fun createSimpleInput(
        namedControl: ParameterControlList.NamedParameterControl, control: BusControl,
    ): Node = createDetailInput(namedControl, control, null)

    override fun createInitialControl(
        obj: ParameterizedObject,
        spec: ControlSpec?,
        oldControl: ParameterControl?,
        parameterName: String,
        ev: Event?,
    ): BusControl {
        spec as BusControlSpec
        val bus = oldControl?.getControlBus() ?: obj.context[BusRegistry].getDefault().reference()
        return BusControl(reactiveVariable(bus))
    }

    override fun supportsDialogInput(): Boolean = true

    override fun showDialogInput(
        parameterName: String, specs: List<ControlSpec>, controls: List<BusControl>, context: Context,
    ): Boolean {
        val busSpecs = specs.filterIsInstance<BusControlSpec>()
        if (busSpecs.size != specs.size) {
            Logger.warn("Some specs are not BusControlSpec", Logger.Category.Score)
            return false
        }
        val channels = busSpecs.map { it.channels }.distinct().singleOrNull()
        if (channels == null) {
            Logger.warn("Some BusControlSpec do not have the same number of channels", Logger.Category.Score)
            return false
        }
        val rate = busSpecs.map { it.rate }.distinct().singleOrNull()
        if (rate == null) {
            Logger.warn("Some BusControlSpec do not have the same rate", Logger.Category.Score)
            return false
        }
        val initialOption = controls.map { ctrl -> ctrl.bus.now }.distinct().singleOrNull()?.get()
        val newBus = BusSelectorPrompt(context[BusRegistry], "Choose $parameterName", rate, channels)
            .showPopup(context[primaryStage], null, initialOption) ?: return false
        context.compoundEdit("Update $parameterName") {
            for (ctrl in controls) {
                VariableEdit.updateVariable(
                    ctrl.bus, newBus.reference(),
                    context[UndoManager], "Update $parameterName"
                )
            }
        }
        return true
    }

    override fun actions(
        namedControl: ParameterControlList.NamedParameterControl,
        control: BusControl,
        view: ScoreObjectView?,
    ): List<ContextualizedAction> = listOf(ServerActions.scopeBus.withContext(control.bus))

    override fun toString(): String = "Bus"
}