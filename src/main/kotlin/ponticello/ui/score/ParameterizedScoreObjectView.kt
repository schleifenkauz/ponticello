package ponticello.ui.score

import fxutils.prompt.SimpleSearchableListView
import fxutils.setupDropArea
import javafx.beans.property.SimpleDoubleProperty
import javafx.geometry.Point2D
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.MouseEvent
import ponticello.impl.json
import ponticello.model.obj.BufferObject
import ponticello.model.obj.BusObject
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.project.UIState
import ponticello.model.registry.BufferRegistry
import ponticello.model.registry.BusRegistry
import ponticello.model.score.Envelope
import ponticello.model.score.ParameterControlList
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.ParameterizedScoreObject
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.controls.*
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.ParameterType
import ponticello.ui.impl.getFrom
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.registry.SearchableParameterDefListView
import reaktive.Observer
import reaktive.value.binding.and
import reaktive.value.binding.equalTo
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asReactiveValue
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
        val controlsDisplay = context[UIState].controlsDisplay
        objectPane.layoutYProperty().bind(
            controlsDisplay.equalTo(InlineControlsDisplay.CONTROLS_BAR)
                .and(inlineControls.visibleProperty().asReactiveValue())
                .asObservableValue()
                .flatMap { bar -> if (bar) inlineControls.heightProperty() else SimpleDoubleProperty(0.0) }
        )
        objectPane.prefWidthProperty().bind(widthProperty())
        objectPane.prefHeightProperty().bind(heightProperty().subtract(objectPane.layoutYProperty()))
        objectPane.heightProperty().addListener { _, _, _ -> rescale() }
        children.add(0, objectPane)
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

    private fun canDrop(db: Dragboard): Boolean =
        when {
            db.hasContent(NamedParameterControl.DATA_FORMAT) -> true
            db.hasContent(BusObject.DATA_FORMAT) -> true
            db.hasContent(BufferObject.DATA_FORMAT) -> true
            else -> false
        }

    private fun drop(ev: DragEvent) {
        val db = ev.dragboard
        when {
            db.hasContent(NamedParameterControl.DATA_FORMAT) -> {
                val jsonString = db.getContent(NamedParameterControl.DATA_FORMAT) as String
                val namedControl = json.decodeFromString(NamedParameterControl.serializer(), jsonString)
                obj.controls.duplicateControl(namedControl)
            }

            db.hasContent(BusObject.DATA_FORMAT) -> {
                val bus = db.getFrom(context[BusRegistry], BusObject.DATA_FORMAT) ?: return
                droppedBus(bus, ev)
            }

            db.hasContent(BufferObject.DATA_FORMAT) -> {
                val buffer = db.getFrom(context[BufferRegistry], BufferObject.DATA_FORMAT) ?: return
                droppedBuffer(buffer, ev)
            }
        }
    }

    private fun droppedBus(bus: BusObject, ev: DragEvent) {
        val assignedName = getAssignedName(ev) { spec -> bus.matches(spec) } ?: return
        val control = BusControl.create(bus)
        obj.controls.assignControl(assignedName, control)
    }

    private fun getAssignedName(ev: DragEvent, predicate: (ControlSpec?) -> Boolean): String? {
        val controlOptions = mutableSetOf<String>()
        obj.controls
            .filter { ctrl -> predicate(ctrl.spec.now) }
            .mapTo(controlOptions) { ctrl -> ctrl.name.now }
        obj.def.parameters
            .filter { p -> predicate(p.spec.now) }
            .mapTo(controlOptions) { p -> p.name.now }

        return when (controlOptions.size) {
            0 -> null
            1 -> controlOptions.single()
            else -> SimpleSearchableListView(controlOptions.toList(), "Select linked parameter").showPopup(ev)
        }
    }

    private fun droppedBuffer(buffer: BufferObject, ev: DragEvent) {
        val assignedName = getAssignedName(ev) { spec -> buffer.matches(spec) } ?: return
        val control = BufferControl.create(buffer)
        obj.controls.assignControl(assignedName, control)
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
        if (canvas != null) objectPane.children.remove(canvas)
    } and obj.lfosManager.onDisplay { param, spec, lfo ->
        val canvas = lfoCanvases.getOrPut(param) {
            val c = LFOCanvas(obj)
            c.widthProperty().bind(objectPane.widthProperty())
            c.heightProperty().bind(objectPane.heightProperty())
            objectPane.children.add(c)
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
            val e = EnvelopeEditor(control, envelope, this, pane = objectPane)
            e.repaint()
            envelopeEditors.add(e)
        }
    }

    override fun rescale() {
        super.rescale()
        for (e in envelopeEditors) {
            if (e.namedControl.spec.now is NumericalControlSpec) {
                e.repaint()
            }
        }
    }
}