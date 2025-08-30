package ponticello.ui.score

import fxutils.actions.makeButton
import fxutils.centerChildren
import fxutils.drag.setupDropArea
import fxutils.prompt.DetailPane
import fxutils.sourceWindow
import fxutils.styleClass
import fxutils.undo.UndoManager
import hextant.context.Context
import javafx.event.Event
import javafx.geometry.Point2D
import javafx.scene.layout.*
import javafx.scene.robot.Robot
import ponticello.model.obj.BusObject
import ponticello.model.obj.InstrumentObject
import ponticello.model.obj.ParameterDefObject
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.project.UIState
import ponticello.model.registry.BufferRegistry
import ponticello.model.registry.BusRegistry
import ponticello.model.score.*
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.*
import ponticello.sc.*
import ponticello.ui.actions.ScoreObjectActions
import ponticello.ui.controls.InlineParameterControlsBar
import ponticello.ui.dock.AppLayout
import ponticello.ui.midi.ContextualMidiReceiver
import ponticello.ui.midi.ParameterControlsMidiContext
import ponticello.ui.registry.BufferSelectorPrompt
import ponticello.ui.registry.BusSelectorPrompt
import ponticello.ui.registry.ParameterDefSelectorPrompt
import reaktive.Observer
import reaktive.and
import reaktive.value.binding.map
import reaktive.value.forEach
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveVariable

class SoundProcessView(
    override val obj: SoundProcess, instance: ScoreObjectInstance,
) : ScoreObjectView(instance) {
    private val objectPane = Pane()
    private val spectrogramPainter = SpectrogramPainter(this, obj, objectPane)
    private val envelopeManager = EnvelopeEditorManager(this, objectPane)
    private val attackReleaseOverlay = AttackReleaseOverlay(this)
    private val lfoCanvases = mutableMapOf<NamedParameterControl, LFOCanvas>()
    private lateinit var lfosObserver: Observer
    private lateinit var controlsDisplayObserver: Observer
    private val midiContext by lazy { ParameterControlsMidiContext(obj.controls) }

    init {
        styleClass("sound-process")
    }

    override fun initialize() {
        super.initialize()
        setupDropArea(ParameterizedObjectDropHandler(obj, this))
        lfosObserver = observeLFOs()
        initializeObjectPane()
        spectrogramPainter.initialize()
        envelopeManager.initialize()
        attackReleaseOverlay.initialize()
        context[ContextualMidiReceiver].registerMidiContext(this) {
            midiContext.takeIf { context[AppLayout].get<ScoreObjectDetailPane>(setup = false).isShowing.now }
        }
    }

    override fun configureInlineControls() {
        val instrumentSelector = InstrumentSelectorPopup(context).selectorButton(
            obj.instrumentRef,
            undoManager = context[UndoManager], actionDescription = "Select instrument"
        )
        instrumentSelector.setupDropArea(InstrumentDropHandler(obj.instrumentRef, context))
        inlineControls.children.add(1, instrumentSelector)
        val inlineControlsBar = InlineParameterControlsBar(obj.controls, this)
        inlineControls.children.add(2, inlineControlsBar)
    }

    private fun initializeObjectPane() {
        objectPane.prefWidthProperty().bind(prefWidthProperty())
        objectPane.prefHeightProperty().bind(prefHeightProperty().subtract(objectPane.layoutYProperty()))
        objectPane.heightProperty().addListener { _, _, _ -> rescale() }
        objectPane.backgroundProperty().bind(backgroundColor.map { color ->
            Background(BackgroundFill(color, CornerRadii.EMPTY, null))
        }.asObservableValue())
        children.add(0, objectPane)

        val controlsDisplay = context[UIState].controlsDisplay
        controlsDisplayObserver = controlsDisplay.forEach { display ->
            if (display == InlineControlsDisplay.CONTROLS_BAR) {
                setupInlineControls()
                objectPane.layoutYProperty().bind(inlineControls.heightProperty())
            } else {
                objectPane.layoutYProperty().unbind()
                objectPane.layoutY = 0.0
            }
        }
    }

    override fun setupDetailPane(pane: DetailPane) {
        pane.addItem("Color:", this.colorPicker)
        val viewInstrumentBtn = ScoreObjectActions.localObjectActions.getAction("View definition")
            .withContext(obj)
            .makeButton("medium-icon-button")
        val selectorBtn = InstrumentSelectorPopup(context).selectorButton(
            obj.instrumentRef, undoManager = context[UndoManager], actionDescription = "Select instrument"
        )
        selectorBtn.setupDropArea(InstrumentDropHandler(obj.instrumentRef, context))
        pane.addItem("Instrument: ", HBox(5.0, selectorBtn, viewInstrumentBtn).centerChildren())
        val controlsPane = ParameterControlsPane(obj, this)
        VBox.setVgrow(controlsPane, Priority.ALWAYS)
        pane.children.add(controlsPane)
        context[ContextualMidiReceiver].registerMidiContext(pane) { midiContext }
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

    override fun rescale() {
        super.rescale()
        envelopeManager.rescaleEnvelopes()
        spectrogramPainter.rescaleSpectrogram()
        attackReleaseOverlay.updateOverlay()
    }

    override fun resizedObject(obj: ScoreObject) {
        super.resizedObject(obj)
        spectrogramPainter.displaySpectrogram()
    }

    companion object {
        fun getInitialControls(
            def: InstrumentObject, context: Context, defaultBus: BusObject?, anchor: Point2D?,
        ): ParameterControlList? {
            val map = mutableMapOf<String, ParameterControl>()
            for (param in def.parameters) {
                val name = param.name.now
                val control = when (val spec = param.spec.now) {
                    is BufferControlSpec -> {
                        val buffer = BufferSelectorPrompt(context[BufferRegistry], "Select $name", spec.channels)
                            .showPopup(anchor) ?: return null
                        BufferControl.create(buffer)
                    }

                    is BusControlSpec -> {
                        if (defaultBus != null && defaultBus.matches(spec)) BusControl.create(defaultBus)
                        else {
                            val bus =
                                BusSelectorPrompt(context[BusRegistry], "Select $name", spec.rate, spec.channels)
                                    .showPopup(anchor) ?: return null
                            BusControl.create(bus)
                        }
                    }

                    else -> spec.defaultControl()
                }
                map[name] = control
            }
            return ParameterControlList.from(map)
        }

        fun showNewEnvelopePopup(obj: SoundProcess, ev: Event?) {
            val possibleParameters = obj.def.allParameters()
                .filter { p -> p.spec.now is NumericalControlSpec }
                .filter { p ->
                    val control = obj.controls.controlMap[p.name.now]
                    control !is EnvelopeControl || !control.display.now
                } + obj.controls
                .filter { ctrl -> ctrl.spec.now is NumericalControlSpec && ctrl.now !is EnvelopeControl }
                .filter { ctrl -> !obj.def.hasParameter(ctrl.name.now) }
                .map { ctrl -> ParameterDefObject(ctrl.name.now, ctrl.spec.now!!) }
            val listView = ParameterDefSelectorPrompt(
                possibleParameters, "New parameter", obj, fixedParameterType = ParameterType.Numerical
            )
            val param = listView.showPopup(Robot().mousePosition, ev.sourceWindow) ?: return
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
    }
}