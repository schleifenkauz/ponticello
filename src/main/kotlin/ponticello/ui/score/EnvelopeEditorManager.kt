package ponticello.ui.score

import javafx.scene.layout.Pane
import ponticello.model.score.Envelope
import ponticello.model.score.ParameterControlList
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.EnvelopeControl
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import reaktive.Observer
import reaktive.value.now

class EnvelopeEditorManager(
    private val view: SoundProcessView, private val objectPane: Pane,
) : ParameterControlList.Listener {
    private val displayObservers = mutableMapOf<EnvelopeControl, Observer>()
    private val envelopeEditors = mutableListOf<EnvelopeEditor>()

    fun initialize() {
        view.obj.controls.addListener(this)
    }

    fun rescaleEnvelopes() {
        for (e in envelopeEditors) {
            val validSpec = e.namedControl.spec.now is NumericalControlSpec
            if (validSpec) {
                e.repaint()
            }
        }
    }

    override fun added(obj: NamedParameterControl, idx: Int) {
        val ctrl = obj.now
        if (ctrl is EnvelopeControl) {
            addedEnvelopeControl(obj, ctrl)
        }
    }

    override fun removed(obj: NamedParameterControl) {
        val ctrl = obj.now
        if (ctrl is EnvelopeControl) {
            displayObservers[ctrl]?.kill()
            removeEnvelope(obj)
        }
    }

    override fun reassignedControl(
        parameter: NamedParameterControl, oldControl: ParameterControl, newControl: ParameterControl,
    ) {
        displayObservers.remove(oldControl)?.kill()
        if (oldControl is EnvelopeControl) removeEnvelope(parameter)
        if (newControl is EnvelopeControl) addedEnvelopeControl(parameter, newControl)
    }

    override fun changedSpec(parameter: NamedParameterControl, oldSpec: ControlSpec?, newSpec: ControlSpec?) {
        val ctrl = parameter.now
        if (ctrl is EnvelopeControl) {
            if (ctrl.display.now) {
                envelopeEditors.find { ed -> ed.namedControl == parameter }?.repaint()
            }
        }
    }

    private fun addedEnvelopeControl(control: NamedParameterControl, env: EnvelopeControl) {
        if (env.display.now) displayEnvelope(control, env.points)
        displayObservers[env] = env.display.observe { _, _, display ->
            if (display) displayEnvelope(control, env.points)
            else removeEnvelope(control)
        }
    }

    private fun displayEnvelope(control: NamedParameterControl, envelope: Envelope) {
        if (control.spec.now is NumericalControlSpec) {
            val e = EnvelopeEditor(control, envelope, view, objectPane)
            e.repaint()
            envelopeEditors.add(e)
        }
    }

    private fun removeEnvelope(control: NamedParameterControl) {
        val ed = envelopeEditors.find { ed -> ed.namedControl == control } ?: return
        ed.dispose()
        envelopeEditors.remove(ed)
    }
}