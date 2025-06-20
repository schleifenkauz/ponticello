package ponticello.ui.score

import bundles.createBundle
import fxutils.actions.button
import fxutils.centerChildren
import fxutils.disableIf
import fxutils.drag.setupDropArea
import fxutils.prompt.DetailPane
import fxutils.styleClass
import javafx.beans.property.SimpleDoubleProperty
import javafx.geometry.Point2D
import javafx.scene.layout.*
import javafx.scene.paint.Color
import org.kordamp.ikonli.material2.Material2AL
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.project.UIState
import ponticello.model.score.Envelope
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.SoundProcess
import ponticello.model.score.controls.EnvelopeControl
import ponticello.model.score.controls.getNumericalValue
import ponticello.sc.NumericalControlSpec
import ponticello.sc.ParameterType
import ponticello.sc.view.ObjectSelectorControl
import ponticello.ui.controls.InlineParameterControlsBar
import ponticello.ui.dock.AppLayout
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.registry.InstrumentRegistryPane
import ponticello.ui.registry.SearchableParameterDefListView
import reaktive.Observer
import reaktive.and
import reaktive.value.ReactiveValue
import reaktive.value.binding.*
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asReactiveValue
import reaktive.value.now
import reaktive.value.reactiveValue
import reaktive.value.reactiveVariable

class SoundProcessView(
    override val obj: SoundProcess, instance: ScoreObjectInstance,
) : ScoreObjectView(instance) {
    private val objectPane = Pane()
    private val spectrogramPainter = SpectrogramPainter(this, obj, objectPane)
    private val envelopeManager = EnvelopeEditorManager(this, objectPane)
    private val lfoCanvases = mutableMapOf<NamedParameterControl, LFOCanvas>()
    private lateinit var lfosObserver: Observer

    override val defaultBackgroundColor: ReactiveValue<Color>
        get() = obj.instrumentSelector.result.flatMap { ref -> ref.get()?.color ?: reactiveValue(Color.GRAY) }

    init {
        styleClass("sound-process")
    }

    override fun initialize() {
        super.initialize()
        setupDropArea(SoundProcessDropHandler(this))
        lfosObserver = observeLFOs()
        val controlsDisplay = context[UIState].controlsDisplay
        objectPane.layoutYProperty().bind(
            controlsDisplay.equalTo(InlineControlsDisplay.CONTROLS_BAR)
                .and(inlineControls.visibleProperty().asReactiveValue())
                .asObservableValue()
                .flatMap { bar -> if (bar) inlineControls.heightProperty() else SimpleDoubleProperty(0.0) }
        )
        objectPane.prefWidthProperty().bind(prefWidthProperty())
        objectPane.prefHeightProperty().bind(prefHeightProperty().subtract(objectPane.layoutYProperty()))
        objectPane.heightProperty().addListener { _, _, _ -> rescale() }
        objectPane.backgroundProperty().bind(backgroundColor.map { color ->
            Background(BackgroundFill(color, CornerRadii.EMPTY, null))
        }.asObservableValue())
        children.add(0, objectPane)

        spectrogramPainter.initialize()
        envelopeManager.initialize()

        if (!parentPane.isRoot(obj)) {
            val synthDefSelector = ObjectSelectorControl(obj.instrumentSelector)
            inlineControls.children.add(1, synthDefSelector)
            val inlineControlsBar = InlineParameterControlsBar(obj.controls, this)
            inlineControls.children.add(2, inlineControlsBar)
        }
    }

    override fun setupDetailPane(pane: DetailPane) {
        pane.addItem("Color:", this.colorPicker)
        val viewBtn = Material2AL.CODE.button("View SynthDef", "medium-icon-button") {
            context[AppLayout].get<InstrumentRegistryPane>().showContent(obj.instrument)
        }.disableIf(obj.instrumentSelector.isResolved.not())
        val box = ObjectSelectorControl(obj.instrumentSelector, createBundle())
        pane.addItem("SynthDef: ", HBox(5.0, box, viewBtn).centerChildren())
        val controlsPane = ParameterControlsPane(obj, this)
        VBox.setVgrow(controlsPane, Priority.ALWAYS)
        pane.children.add(controlsPane)
    }

    fun showNewEnvelopePopup() {
        val possibleParameters = obj.def.allParameters()
            .filter { p -> p.spec.now is NumericalControlSpec }
            .filter { p ->
                val control = obj.controls.controlMap[p.name.now]
                control !is EnvelopeControl || !control.display.now
            }
        val listView = SearchableParameterDefListView(
            possibleParameters, "New parameter", obj.context, obj, fixedParameterType = ParameterType.Numerical
        )
        val relativeY = if (inlineControls.isVisible) inlineControls.height else 0.0
        val anchor = localToScreen(Point2D(0.0, relativeY))
        val param = listView.showPopup(anchor, context[primaryStage]) ?: return
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

    override fun rescale() {
        super.rescale()
        envelopeManager.rescaleEnvelopes()
        spectrogramPainter.rescaleSpectrogram()
    }

    override fun resizedObject(obj: ScoreObject) {
        super.resizedObject(obj)
        spectrogramPainter.displaySpectrogram()
    }
}