package xenakis.ui.score

import javafx.geometry.Point2D
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Region
import reaktive.Observer
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.Settings
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.*
import xenakis.model.score.ParameterControlList.NamedParameterControl
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.ui.registry.SearchableParameterListView

abstract class ParameterizedScoreObjectView<O>(
    instance: ScoreObjectInstance
) : ScoreObjectView(instance), ParameterControlList.Listener where O : ScoreObject, O : ParameterizedObject {
    private val envelopeDisplayObservers = mutableMapOf<EnvelopeControl, Observer>()
    private val envelopeEditors = mutableListOf<EnvelopeEditor>()

    abstract val obj: O

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        listenForMouseEvents()
        obj.controls.addListener(this)
    }

    private fun listenForMouseEvents() {
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            if (ev.isAltDown) {
                val p = Point2D(ev.screenX, ev.screenY)
                showNewEnvelopePopup(p)
                ev.consume()
            }
        }
    }

    private fun showNewEnvelopePopup(point: Point2D) {
        val possibleParameters = obj.def.parameters
            .filter { p -> p.spec.now is NumericalControlSpec }
            .filter { p ->
                val control = obj.controls.controlMap[p.name.now]
                control !is EnvelopeControl || !control.display.now
            }
        val listView = SearchableParameterListView(context, "Add new envelope", obj, possibleParameters)
        listView.showPopup(point) { param ->
            val name = param.name.now
            val spec = param.spec.now as NumericalControlSpec
            val initialValue = obj.controls.controlMap[name]?.getNumericalValue() ?: spec.defaultValue.get()
            val env = Envelope.constant(initialValue, obj.duration, spec.warp)
            val control = EnvelopeControl(
                env, reactiveVariable(spec.associatedColor),
                display = reactiveVariable(true)
            )
            val customSpec = spec.takeIf {
                it != obj.def.getSpec(param.name.now)?.now
            }
            if (name !in obj.controls.controlMap) obj.controls.addControl(name, control, customSpec)
            else obj.controls.reassignControl(name, control)
        }
    }

    override fun added(control: NamedParameterControl, idx: Int) {
        when (val ctrl = control.now) {
            is EnvelopeControl -> addedEnvelopeControl(control, ctrl)
            else -> {}
        }
    }

    private fun addedEnvelopeControl(control: NamedParameterControl, env: EnvelopeControl) {
        if (env.display.now) displayEnvelope(control, env.envelope)
        envelopeDisplayObservers[env] = env.display.observe { _, _, display ->
            if (display) displayEnvelope(control, env.envelope)
            else removeEnvelope(control)
        }
    }

    override fun removed(control: NamedParameterControl) {
        when (val ctrl = control.now) {
            is EnvelopeControl -> removedEnvelopeControl(control, ctrl)
            else -> {}
        }
    }

    override fun reassignedControl(
        namedControl: NamedParameterControl,
        oldControl: ParameterControl,
        control: ParameterControl
    ) {
        if (oldControl is EnvelopeControl) removedEnvelopeControl(namedControl, oldControl)
        if (control is EnvelopeControl) addedEnvelopeControl(namedControl, control)
    }

    private fun removedEnvelopeControl(control: NamedParameterControl, env: EnvelopeControl) {
        removeEnvelope(control)
        envelopeDisplayObservers.remove(env)!!.kill()
    }

    override fun changedSpec(control: NamedParameterControl, oldSpec: ControlSpec?, newSpec: ControlSpec?) {
        envelopeEditors.find { ed -> ed.namedControl == control }?.repaint()
    }

    private fun removeEnvelope(control: NamedParameterControl) {
        val ed = envelopeEditors.find { ed -> ed.namedControl == control }
            ?: error("envelope editor for $control not found")
        ed.dispose()
        envelopeEditors.remove(ed)
    }

    private fun displayEnvelope(control: NamedParameterControl, envelope: Envelope) {
        if (control.spec.now is NumericalControlSpec) {
            val e = EnvelopeEditor(control, envelope, this, pane = this)
            e.repaint()
            envelopeEditors.add(e)
        }
    }

    override fun rescale() {
        for (e in envelopeEditors) {
            if (e.namedControl.spec.now is NumericalControlSpec) {
                e.repaint()
            }
        }
    }

    companion object {
        fun addNewControl(obj: ParameterizedObject, anchorNode: Region) {
            val context = obj.context
            val defaultParameters = context[Settings].defaultParametersDefs
            val synthParameters = obj.def.parameters
            val unassignedParameters = (synthParameters + defaultParameters)
                .filter { param -> param.name.now !in obj.controls.controlMap }
                .filter { param -> !(param in defaultParameters && synthParameters.any { p -> p.name.now == param.name.now }) }
            val listView = SearchableParameterListView(
                context, "Add new control",
                obj, unassignedParameters
            )
            listView.showPopup(anchorNode = anchorNode) { option ->
                val parameter = option.name.now
                val customSpec = option.spec.now.takeIf { !obj.def.hasParameter(parameter) }
                val control = option.defaultControl(context)
                obj.controls.addControl(parameter, control, customSpec)
            }
        }
    }
}