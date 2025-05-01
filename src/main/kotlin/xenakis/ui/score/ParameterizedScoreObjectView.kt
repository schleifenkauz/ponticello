package xenakis.ui.score

import javafx.geometry.Point2D
import javafx.scene.input.MouseEvent
import reaktive.Observer
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.score.Envelope
import xenakis.model.score.ParameterControlList
import xenakis.model.score.ParameterControlList.NamedParameterControl
import xenakis.model.score.ParameterizedScoreObject
import xenakis.model.score.ScoreObjectInstance
import xenakis.model.score.controls.EnvelopeControl
import xenakis.model.score.controls.ParameterControl
import xenakis.model.score.controls.getNumericalValue
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.sc.ParameterType
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage
import xenakis.ui.registry.SearchableParameterDefListView

abstract class ParameterizedScoreObjectView<O : ParameterizedScoreObject>(
    instance: ScoreObjectInstance,
) : ScoreObjectView(instance), ParameterControlList.Listener {
    private val observers = mutableMapOf<ParameterControl, Observer>()
    private val envelopeEditors = mutableListOf<EnvelopeEditor>()
    private val lfoCanvases = mutableMapOf<NamedParameterControl, LFOCanvas>()
    private lateinit var lfosObserver: Observer

    abstract override val obj: O

    override fun initialize() {
        super.initialize()
        listenForMouseEvents()
        obj.controls.addListener(this)
        lfosObserver = observeLFOs()
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
        val listView = SearchableParameterDefListView(
            possibleParameters, "New parameter", obj,
            context[primaryStage], point, fixedParameterType = ParameterType.Numerical
        )
        val param = listView.showPopup() ?: return
        val name = param.name.now
        val spec = param.spec.now as NumericalControlSpec
        val initialValue = obj.controls.controlMap[name]?.getNumericalValue() ?: spec.defaultValue.get()
        val env = Envelope.constant(initialValue, obj.duration)
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

    private fun observeLFOs() = obj.lfosManager.onRemove { param ->
        val canvas = lfoCanvases.remove(param)
        if (canvas != null) children.remove(canvas)
    } and obj.lfosManager.onDisplay { param, spec, lfo ->
        val canvas = lfoCanvases.getOrPut(param) {
            val c = LFOCanvas(obj)
            c.widthProperty().bind(widthProperty())
            c.heightProperty().bind(heightProperty())
            children.add(c)
            c
        }
        canvas.display(lfo, spec)
    }

    override fun added(obj: NamedParameterControl, idx: Int) {
        when (val ctrl = obj.now) {
            is EnvelopeControl -> addedEnvelopeControl(obj, ctrl)
            else -> {}
        }
    }

    private fun addedEnvelopeControl(control: NamedParameterControl, env: EnvelopeControl) {
        if (env.display.now) displayEnvelope(control, env.points)
        observers[env] = env.display.observe { _, _, display ->
            if (display) displayEnvelope(control, env.points)
            else removeEnvelope(control)
        }
    }

    override fun removed(obj: NamedParameterControl) {
        observers.remove(obj.now)?.kill()
        when (obj.now) {
            is EnvelopeControl -> removeEnvelope(obj)
            else -> {}
        }
    }

    override fun reassignedControl(
        parameter: NamedParameterControl,
        oldControl: ParameterControl,
        newControl: ParameterControl,
    ) {
        observers.remove(oldControl)?.kill()
        if (oldControl is EnvelopeControl) removeEnvelope(parameter)
        if (newControl is EnvelopeControl) addedEnvelopeControl(parameter, newControl)
    }

    override fun changedSpec(parameter: NamedParameterControl, oldSpec: ControlSpec?, newSpec: ControlSpec?) {
        when (val ctrl = parameter.now) {
            is EnvelopeControl -> {
                if (ctrl.display.now) {
                    envelopeEditors.find { ed -> ed.namedControl == parameter }?.repaint()
                }
            }

            else -> {}
        }

    }

    private fun removeEnvelope(control: NamedParameterControl) {
        val ed = envelopeEditors.find { ed -> ed.namedControl == control } ?: return
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
}