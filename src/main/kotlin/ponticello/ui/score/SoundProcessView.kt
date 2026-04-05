package ponticello.ui.score

import fxutils.actions.makeButton
import fxutils.centerChildren
import fxutils.drag.setupDropArea
import fxutils.prompt.DetailPane
import fxutils.prompt.PromptPlacement
import fxutils.styleClass
import fxutils.undo.UndoManager
import hextant.context.Context
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.scene.layout.*
import ponticello.impl.Decimal
import ponticello.impl.one
import ponticello.impl.zero
import ponticello.model.instr.BusObject
import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.MidiInstrument
import ponticello.model.instr.ParameterDefObject
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.project.UIState
import ponticello.model.score.*
import ponticello.model.score.controls.*
import ponticello.model.server.BufferRegistry
import ponticello.model.server.BusRegistry
import ponticello.model.server.SampleObject
import ponticello.sc.*
import ponticello.ui.actions.ScoreObjectActions
import ponticello.ui.controls.InlineParameterControlsBar
import ponticello.ui.midi.MidiContext
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
    private var generatedScorePane: SubScorePane? = null
    private val samplePainter = SamplePainter(this, objectPane)
    private val envelopeManager = EnvelopeEditorManager(this, objectPane)
    private val attackReleaseOverlay = AttackReleaseOverlay(this)
    private val lfoCanvases = mutableMapOf<NamedParameterControl, LFOCanvas>()
    private lateinit var lfosObserver: Observer
    private lateinit var controlsDisplayObserver: Observer
    override val tempoGrid: TempoGrid?
        get() {
            val sample = obj.sample.now?.get() as? SampleObject ?: return null
            val meter = sample.meter
            if (meter.isNone()) return null
            val rate = obj.bufferStretchFactor?.now ?: one
            val startPos = obj.bufferOffset?.now ?: zero
            val offset = (startPos / rate) - sample.firstBeat.now
            return TempoGrid(
                type = TempoGrid.GridType.SampleOverlay, scoreObject = obj,
                getPosition = this::absolutePosition,
                meter = meter, offset = offset, scale = rate,
                canvas = samplePainter.gridCanvas, marker = samplePainter.marker
            )
        }

    init {
        styleClass("sound-process")
    }

    override fun initialize() {
        super.initialize()
        setupDropArea(ParameterizedObjectDropHandler(obj, this))
        lfosObserver = observeLFOs()
        initializeObjectPane()
        samplePainter.initialize()
        envelopeManager.initialize()
        attackReleaseOverlay.initialize()
        generatedScorePane?.initialize()
    }

    override fun configureInlineControls() {
        val instrumentSelector = InstrumentSelectorPrompt(context).selectorButton(
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
        objectPane.heightProperty().addListener { _, _, _ -> rescale() } //TODO necessary?
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

    override fun setupDetailPane(pane: DetailPane, midiContext: MidiContext?) {
        pane.addItem("Color:", this.colorPicker)
        if (obj.getInstrument() !is MidiInstrument) {
            val viewInstrumentBtn = ScoreObjectActions.localObjectActions.getAction("View definition")
                .withContext(obj)
                .makeButton("medium-icon-button")
            val selectorBtn = InstrumentSelectorPrompt(context).selectorButton(
                obj.instrumentRef, undoManager = context[UndoManager], actionDescription = "Select instrument"
            )
            selectorBtn.setupDropArea(InstrumentDropHandler(obj.instrumentRef, context))
            pane.addItem("Instrument: ", HBox(5.0, selectorBtn, viewInstrumentBtn).centerChildren())
        }
        val controlsPane = ParameterControlsPane(obj, this, midiContext)
        VBox.setVgrow(controlsPane, Priority.ALWAYS)
        pane.children.add(controlsPane)
    }

    private fun observeLFOs() = obj.lfosManager.onRemove { param ->
        val canvas = lfoCanvases.remove(param)
        if (canvas != null) objectPane.children.remove(canvas)
    } and obj.lfosManager.onDisplay { param, spec, lfo ->
        val canvas = lfoCanvases.getOrPut(param) {
            val c = LFOCanvas(obj)
            c.viewOrder = 500.0
            c.widthProperty().bind(Bindings.min(MAX_OBJECT_WIDTH, objectPane.prefWidthProperty()))
            c.heightProperty().bind(objectPane.heightProperty())
            objectPane.children.add(c)
            c
        }
        canvas.display(lfo, spec)
    }

    fun generatedScore(score: Score?, yScale: Decimal) = Platform.runLater {
        val oldScorePane = generatedScorePane
        if (oldScorePane != null) {
            children.remove(oldScorePane)
            generatedScorePane = null
        }
        if (score != null) {
            val pane = SubScorePane(score, this, yScale)
            pane.prefWidthProperty().bind(prefWidthProperty())
            pane.prefHeightProperty().bind(prefHeightProperty().subtract(objectPane.layoutYProperty()))
            pane.backgroundProperty().bind(backgroundColor.map { color ->
                Background(BackgroundFill(color, CornerRadii.EMPTY, null))
            }.asObservableValue())
            generatedScorePane = pane
            if (isInitialized) pane.initialize()
        } else {
            if (objectPane !in children) children.add(objectPane)
        }
    }

    fun useGeneratedScore(use: Boolean) = Platform.runLater {
        if (use) {
            children.remove(objectPane)
            if (generatedScorePane == null) {
                generatedScore(obj.generatedScore, obj.generatedScoreYScale)
            }
            if (generatedScorePane !in children) {
                children.add(generatedScorePane)
            }
        } else {
            children.remove(generatedScorePane)
            if (objectPane !in children) children.add(objectPane)
        }
    }

    override fun rescale() {
        super.rescale()
        envelopeManager.rescaleEnvelopes()
        samplePainter.rescale()
        attackReleaseOverlay.updateOverlay()
        generatedScorePane?.repaint()
    }

    override fun resizedObject(obj: ScoreObject) {
        super.resizedObject(obj)
        samplePainter.displaySpectrogram()
    }

    override fun relocate(x: Double, y: Double) {
        layoutX
        super.relocate(x, y)
        samplePainter.relocated()
    }

    companion object {
        fun getInitialControls(
            def: InstrumentObject, context: Context, defaultBus: BusObject?, promptPlacement: PromptPlacement,
        ): ParameterControlList? {
            val map = mutableMapOf<String, ParameterControl>()
            for (param in def.parameters) {
                val name = param.name.now
                val control = when (val spec = param.spec.now) {
                    is BufferControlSpec -> {
                        val buffer = BufferSelectorPrompt(context[BufferRegistry], "Select $name", spec.channels)
                            .showDialog(promptPlacement) ?: return null
                        BufferControl.create(buffer)
                    }

                    is BusControlSpec -> {
                        if (defaultBus != null && defaultBus.matches(spec)) BusControl.create(defaultBus)
                        else {
                            val bus =
                                BusSelectorPrompt(context[BusRegistry], "Select $name", spec.rate, spec.channels)
                                    .showDialog(promptPlacement) ?: return null
                            BusControl.create(bus)
                        }
                    }

                    else -> spec.defaultControl()
                }
                map[name] = control
            }
            return ParameterControlList.from(map)
        }

        fun showNewEnvelopePopup(obj: SoundProcess, placement: PromptPlacement) {
            val possibleParameters = obj.getInstrument().allParameters()
                .filter { p -> p.spec.now is NumericalControlSpec }
                .filter { p ->
                    val control = obj.controls.controlMap[p.name.now]
                    control !is EnvelopeControl || !control.display.now
                } + obj.controls
                .filter { ctrl -> ctrl.spec.now is NumericalControlSpec && ctrl.now !is EnvelopeControl }
                .filter { ctrl -> !obj.getInstrument().hasParameter(ctrl.name.now) }
                .map { ctrl -> ParameterDefObject(ctrl.name.now, ctrl.spec.now!!) }
            if (possibleParameters.isEmpty()) return
            val listView = ParameterDefSelectorPrompt(
                possibleParameters, "Automate parameter", obj, fixedParameterType = ParameterType.Numerical
            )
            val param = listView.showPopup(placement) ?: return
            val name = param.name.now
            val spec = param.spec.now as NumericalControlSpec
            val initialValue = obj.controls.controlMap[name]?.getNumericalValue() ?: spec.defaultValue.get()
            val env = Envelope.constant(initialValue, obj.duration)
            val control = EnvelopeControl(
                env, reactiveVariable(spec.associatedColor),
                display = reactiveVariable(true)
            )
            val customSpec = spec.takeIf {
                it != obj.getInstrument().getSpec(param.name.now)?.now
            }
            if (name !in obj.controls.controlMap) obj.controls.addControl(name, control, customSpec)
            else obj.controls.reassignControl(name, control)
        }
    }
}