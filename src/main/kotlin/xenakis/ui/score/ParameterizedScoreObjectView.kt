package xenakis.ui.score

import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import javafx.scene.layout.FlowPane
import javafx.scene.layout.HBox
import reaktive.Observer
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.Settings
import xenakis.model.score.*
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.ui.Icon
import xenakis.ui.controls.ControlAssignmentView
import xenakis.ui.controls.DetailPane
import xenakis.ui.controls.Knob
import xenakis.ui.impl.centerChildren
import xenakis.ui.impl.infiniteSpace
import xenakis.ui.impl.styleClass
import xenakis.ui.registry.SearchableParameterListView

abstract class ParameterizedScoreObjectView(
    instance: ScoreObjectInstance
) : ScoreObjectView(instance), ParameterControls.View {
    private val envelopeDisplayObservers = mutableMapOf<String, Observer>()
    private val knobControls = FlowPane().centerChildren() styleClass "knobs" //TODO remove
    private val envelopeEditors = mutableListOf<EnvelopeEditor>()

    abstract val obj: ParameterizedScoreObject

    override fun setupDetailPane(pane: DetailPane) {
        val addButton = Icon.Add.button(action = "Add control")
        val header = HBox(
            5.0,
            Label("Synth controls").styleClass("heading"),
            infiniteSpace(),
            addButton
        ) styleClass "tool-pane-header"
        addButton.setOnMouseClicked { ev ->
            if (ev.isShiftDown) {
                addControlsForAllObjectParameters()
            } else {
                addNewControl(addButton)
            }
        }
        pane.children.addAll(header, ControlAssignmentView(obj))
    }

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        listenForMouseEvents()
    }

    private fun addControlsForAllObjectParameters() {
        for (param in obj.def.parameters.now) {
            val name = param.name.now
            if (name !in obj.controls.controlMap) {
                obj.controls.addControl(name, param.defaultControl(context))
            }
        }
    }

    private fun addNewControl(anchorNode: Node) {
        val defaultParameters = context[Settings].defaultParametersDefs.now
        val synthParameters = obj.def.parameters.now
        val unassignedParameters = (synthParameters + defaultParameters)
            .filter { param -> param.name.now !in obj.controls.controlMap }
            .filter { param -> !(param in defaultParameters && synthParameters.any { p -> p.name.now == param.name.now }) }
        val listView = SearchableParameterListView(context, "Add new control", obj, unassignedParameters)
        listView.showPopup(context, anchorNode = anchorNode) { option ->
            val parameter = option.name.now
            if (!obj.def.hasParameter(parameter)) {
                obj.controls.setExtraSpec(parameter, option.spec.now)
            }
            obj.controls.addControl(parameter, option.defaultControl(context))
        }
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
        val possibleParameters = obj.parameters
            .filter { p -> p.spec.now is NumericalControlSpec }
            .filter { p ->
                val control = obj.controls.controlMap[p.name.now]
                control !is EnvelopeControl || !control.display.now
            }
        val listView = SearchableParameterListView(context, "Add new envelope", obj, possibleParameters)
        listView.showPopup(context, point) { param ->
            val name = param.name.now
            val spec = param.spec.now as NumericalControlSpec
            val initialValue = obj.controls.controlMap[name]?.getNumericalValue() ?: spec.defaultValue.get()
            val env = Envelope.constant(initialValue, obj.duration, spec.warp)
            val control = EnvelopeControl(
                env, reactiveVariable(spec.associatedColor),
                display = reactiveVariable(true)
            )
            val extraSpec = param.spec.now.takeIf {
                name !in obj.def.parameters.now.map { p -> p.name.now }
            }
            if (!obj.def.hasParameter(name)) {
                obj.controls.setExtraSpec(name, extraSpec)
            }
            if (name !in obj.controls.controlMap) obj.controls.addControl(name, control)
            else obj.controls.reassignControl(name, control)
        }
    }

    override fun removedControl(parameter: String, control: ParameterControl) {
        when (control) {
            is EnvelopeControl -> removedEnvelopeControl(parameter)
            is KnobControl -> removeKnob(parameter)
            else -> {}
        }
    }

    private fun removedEnvelopeControl(parameter: String) {
        removeEnvelope(parameter)
        envelopeDisplayObservers.remove(parameter)!!.kill()
    }

    override fun addedControl(parameter: String, control: ParameterControl) {
        when (control) {
            is EnvelopeControl -> addedDisplayControl(control, parameter)
            is KnobControl -> displayKnob(parameter, control)
            else -> {}
        }
    }

    override fun changedSpec(parameter: String, newSpec: ControlSpec) {
        envelopeEditors.find { ed -> ed.parameterName == parameter }?.repaint()
    }

    private fun removeEnvelope(parameter: String) {
        val ed = envelopeEditors.find { ed -> ed.parameterName == parameter }
            ?: error("envelope editor for $parameter not found")
        ed.dispose()
        envelopeEditors.remove(ed)
    }

    private fun addedDisplayControl(control: EnvelopeControl, parameter: String) {
        if (control.display.now) displayEnvelope(parameter, control)
        envelopeDisplayObservers[parameter] = control.display.observe { _, _, display ->
            if (display) displayEnvelope(parameter, control)
            else removeEnvelope(parameter)
        }
    }

    private fun displayEnvelope(parameter: String, control: EnvelopeControl) {
        val envelope = control.envelope
        val e = EnvelopeEditor(parameter, envelope, this, pane = this)
        e.repaint()
        envelopeEditors.add(e)
    }

    private fun removeKnob(parameter: String) {
        knobControls.children.removeIf { k -> k is Knob && k.parameter == parameter }
    }

    private fun displayKnobs() {
        knobControls.children.clear()
        for ((parameter, control) in instance.obj.associatedControls) {
            if (control !is KnobControl) continue
            displayKnob(parameter, control)
        }
    }

    override fun rescale() {
        for (e in envelopeEditors) {
            e.repaint()
        }
    }

    private fun displayKnob(parameter: String, control: KnobControl) {
        val spec = instance.obj.getSpec(parameter) as NumericalControlSpec
        val knob = Knob(parameter, control, spec, radius = 24.0, context)
        knobControls.children.add(knob)
    }
}