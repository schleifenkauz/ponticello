package ponticello.ui.score

import fxutils.setupDropArea
import javafx.geometry.Point2D
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.MouseEvent
import ponticello.impl.json
import ponticello.model.score.Envelope
import ponticello.model.score.ParameterControlList
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.ParameterizedScoreObject
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.controls.EnvelopeControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.getNumericalValue
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.ParameterType
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.registry.SearchableParameterDefListView
import reaktive.Observer
import reaktive.value.now
import reaktive.value.reactiveVariable

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
        setupDropArea(::canDrop, ::drop)
    }

    private fun canDrop(db: Dragboard): Boolean = db.hasContent(NamedParameterControl.DATA_FORMAT)

    private fun drop(ev: DragEvent) {
        val db = ev.dragboard
        when {
            db.hasContent(NamedParameterControl.DATA_FORMAT) -> {
                val jsonString = db.getContent(NamedParameterControl.DATA_FORMAT) as String
                val namedControl = json.decodeFromString(NamedParameterControl.serializer(), jsonString)
                obj.controls.duplicateControl(namedControl)
            }
        }
    }

    private fun showNewEnvelopePopup(point: Point2D) {
        val possibleParameters = obj.def.allParameters()
            .filter { p -> p.spec.now is NumericalControlSpec }
            .filter { p ->
                val control = obj.controls.controlMap[p.name.now]
                control !is EnvelopeControl || !control.display.now
            }
        val listView = SearchableParameterDefListView(
            possibleParameters, "New parameter", obj, fixedParameterType = ParameterType.Numerical
        )
        val param = listView.showPopup(point, context[primaryStage]) ?: return
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