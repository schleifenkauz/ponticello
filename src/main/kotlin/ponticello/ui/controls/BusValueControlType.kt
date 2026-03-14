package ponticello.ui.controls

import fxutils.actions.ContextualizedAction
import fxutils.actions.action
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import hextant.context.Context
import hextant.context.compoundEdit
import javafx.event.Event
import javafx.scene.Node
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import ponticello.impl.Logger
import ponticello.impl.asY
import ponticello.model.instr.InstrumentRegistry
import ponticello.model.instr.ParameterizedObject
import ponticello.model.project.mainScore
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.model.score.ScoreObject
import ponticello.model.score.SoundProcess
import ponticello.model.score.controls.*
import ponticello.model.server.BusRegistry
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Rate
import ponticello.ui.actions.ServerActions
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.launcher.PonticelloLauncher
import ponticello.ui.registry.BusSelectorPrompt
import ponticello.ui.registry.SimpleRegistrySelectorPrompt
import ponticello.ui.score.ScoreObjectView
import reaktive.value.now
import reaktive.value.reactiveVariable

data object BusValueControlType : ControlType<BusValueControl>() {
    override fun applicableOn(obj: ParameterizedObject, spec: ControlSpec): Boolean =
        spec is NumericalControlSpec

    override fun createDetailInput(
        namedControl: ParameterControlList.NamedParameterControl,
        control: BusValueControl,
        view: ScoreObjectView?,
    ): Node = busSelector(control.bus, namedControl.spec.now, namedControl.context)

    override fun createSimpleInput(
        namedControl: ParameterControlList.NamedParameterControl, control: BusValueControl,
    ): Node = createDetailInput(namedControl, control, null)

    override fun createInitialControl(
        obj: ParameterizedObject,
        spec: ControlSpec?,
        oldControl: ParameterControl?,
        parameterName: String,
        ev: Event?,
    ): BusValueControl {
        if (ev == null) return BusValueControl(reactiveVariable(ObjectReference.none()))
        val initial = oldControl?.getBus() ?: obj.context[BusRegistry].getDefault().reference()
        val title = "Select '${parameterName}'"
        val selected = BusSelectorPrompt(obj.context[BusRegistry], title, rate = Rate.Control, channels = 1)
            .showPopup(ev, initialOption = initial.get()) ?: initial.force()
        return BusValueControl(reactiveVariable(selected.reference()))
    }

    override fun supportsDialogInput(): Boolean = true

    override fun showDialogInput(
        parameterName: String, specs: List<ControlSpec>, controls: List<BusValueControl>, context: Context,
    ): Boolean {
        for (spec in specs) {
            if (spec !is NumericalControlSpec) {
                Logger.warn("Some specs are not NumericalControlSpec", Logger.Category.Score)
                return false
            }
        }
        val initialOption = controls.map { ctrl -> ctrl.bus.now }.distinct().singleOrNull()?.get()
        val newBus = BusSelectorPrompt(context[BusRegistry], "Choose $parameterName", Rate.Control, 1)
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

    override fun toString(): String = "Bus"

    override fun actions(
        namedControl: ParameterControlList.NamedParameterControl,
        control: BusValueControl,
        view: ScoreObjectView?,
    ): List<ContextualizedAction> = listOf(
        ServerActions.scopeBus.withContext(control.bus),
        automateWithProcess.withContext(namedControl)
    )

    private val automateWithProcess = action<ParameterControlList.NamedParameterControl>("Automate with Process") {
        icon(MaterialDesignS.SINE_WAVE)
        applicableIf { ctrl -> ctrl.parentObject is ScoreObject }
        executes { ctrl, ev ->
            val obj = ctrl.parentObject as ScoreObject
            val context = ctrl.context
            val instrumentDef = SimpleRegistrySelectorPrompt(context[InstrumentRegistry], "Choose Instrument")
                .showPopup(ev, initialOption = null) ?: return@executes
            val parameter = ctrl.name.now
            val name = "${obj.name.now}_$parameter"
            val controls = instrumentDef.getDefaultControls(null)
            val outBus = controls["out"]
            if (outBus is BusControl) {
                outBus.bus.now = (ctrl.now as BusValueControl).bus.now
            }
            val synthObj = SoundProcess.create(name, instrumentDef.reference(), ParameterControlList.from(controls))
            synthObj.setInitialSize(obj.duration, height = 0.05.asY)
            context.compoundEdit("Add automation synth") {
                for (inst in context[PonticelloLauncher.currentProject].mainScore.instancesOf(obj).toList()) {
                    inst.score!!.addObject(synthObj, inst.start, inst.y - 0.06.asY, autoSelect = true)
                }
            }
        }
    }
}