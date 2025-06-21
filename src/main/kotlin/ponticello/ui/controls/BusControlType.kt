package ponticello.ui.controls

import fxutils.actions.ContextualizedAction
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import hextant.context.Context
import hextant.context.compoundEdit
import javafx.scene.Node
import javafx.scene.layout.Region
import ponticello.impl.Logger
import ponticello.model.obj.ParameterizedObject
import ponticello.model.registry.BusRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ParameterControlList
import ponticello.model.score.controls.BusControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.getBus
import ponticello.sc.BusControlSpec
import ponticello.sc.ControlSpec
import ponticello.ui.actions.ServerActions
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.registry.SearchableBusListView
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
        oldControl: ParameterControl,
        namedControl: ParameterControlList.NamedParameterControl,
        anchorNode: Region,
    ): BusControl {
        val initial = oldControl.getBus() ?: obj.context[BusRegistry].getDefault().reference()
        return BusControl(reactiveVariable(initial))
    }

    override fun supportsDialogInput(): Boolean = true

    override fun showDialogInput(
        parameterName: String, specs: List<ControlSpec>, controls: List<BusControl>, context: Context,
    ) {
        val busSpecs = specs.filterIsInstance<BusControlSpec>()
        if (busSpecs.size != specs.size) {
            Logger.warn("Some specs are not BusControlSpec", Logger.Category.Score)
            return
        }
        val channels = busSpecs.map { it.channels }.distinct().singleOrNull()
        if (channels == null) {
            Logger.warn("Some BusControlSpec do not have the same number of channels", Logger.Category.Score)
            return
        }
        val rate = busSpecs.map { it.rate }.distinct().singleOrNull()
        if (rate == null) {
            Logger.warn("Some BusControlSpec do not have the same rate", Logger.Category.Score)
            return
        }
        val initialOption = controls.map { ctrl -> ctrl.bus.now }.distinct().singleOrNull()?.get()
        val newBus = SearchableBusListView(context[BusRegistry], "Choose $parameterName", rate, channels)
            .showPopup(context[primaryStage], null, initialOption) ?: return
        context.compoundEdit("Update $parameterName") {
            for (ctrl in controls) {
                VariableEdit.updateVariable(
                    ctrl.bus, newBus.reference(),
                    context[UndoManager], "Update $parameterName"
                )
            }
        }
    }

    override fun actions(
        namedControl: ParameterControlList.NamedParameterControl,
        control: BusControl,
        view: ScoreObjectView?,
    ): List<ContextualizedAction> = listOf(ServerActions.scopeBus.withContext(control.bus))

    override fun toString(): String = "Bus"
}