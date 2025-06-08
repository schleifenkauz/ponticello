package ponticello.ui.controls

import fxutils.actions.ContextualizedAction
import fxutils.actions.action
import hextant.context.compoundEdit
import javafx.scene.Node
import javafx.scene.layout.Region
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import ponticello.impl.asY
import ponticello.model.obj.ParameterizedObject
import ponticello.model.project.mainScore
import ponticello.model.registry.BusRegistry
import ponticello.model.registry.SynthDefRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ParameterControlList
import ponticello.model.score.ScoreObject
import ponticello.model.score.SynthObject
import ponticello.model.score.controls.BusControl
import ponticello.model.score.controls.BusValueControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.getBus
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Rate
import ponticello.ui.actions.ServerActions
import ponticello.ui.launcher.PonticelloLauncher
import ponticello.ui.registry.SearchableBusListView
import ponticello.ui.registry.SimpleSearchableRegistryView
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
        oldControl: ParameterControl,
        namedControl: ParameterControlList.NamedParameterControl,
        anchorNode: Region,
    ): BusValueControl {
        val initial = oldControl.getBus() ?: obj.context[BusRegistry].getDefault().reference()
        val title = "Select '${namedControl.name.now}'"
        val selected = SearchableBusListView(obj.context[BusRegistry], title, rate = Rate.Control, channels = 1)
            .showPopup(anchorNode, initialOption = initial.get()) ?: initial.force()
        return BusValueControl(reactiveVariable(selected.reference()))
    }

    override fun toString(): String = "Bus"

    override fun actions(
        namedControl: ParameterControlList.NamedParameterControl,
        control: BusValueControl,
        view: ScoreObjectView?,
    ): List<ContextualizedAction> = listOf(
        ServerActions.scopeBus.withContext(control.bus),
        automateWithSynth.withContext(namedControl)
    )

    private val automateWithSynth = action<ParameterControlList.NamedParameterControl>("Automate with Synth") {
        icon(MaterialDesignS.SINE_WAVE)
        applicableIf { ctrl -> ctrl.parentObject is ScoreObject }
        executes { ctrl, ev ->
            val obj = ctrl.parentObject as ScoreObject
            val context = ctrl.context
            val synthDef = SimpleSearchableRegistryView(context[SynthDefRegistry], "Choose SynthDef")
                .showPopup(ev, initialOption = null) ?: return@executes
            val parameter = ctrl.name.now
            val name = "${obj.name.now}_$parameter"
            val controls = synthDef.getDefaultControls(null)
            val outBus = controls.getOrNull("out")?.now
            if (outBus is BusControl) {
                outBus.bus.now = (ctrl.now as BusControl).bus.now
            }
            val synthObj = SynthObject.create(name, synthDef, controls)
            synthObj.setInitialSize(obj.duration, height = 0.05.asY)
            context.compoundEdit("Add automation synth") {
                for (inst in context[PonticelloLauncher.currentProject].mainScore.instancesOf(obj).toList()) {
                    inst.score!!.addObject(synthObj, inst.start, inst.y - 0.06.asY, autoSelect = true)
                }
            }
        }
    }
}