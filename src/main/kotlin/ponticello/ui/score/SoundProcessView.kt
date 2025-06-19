package ponticello.ui.score

import bundles.createBundle
import fxutils.actions.button
import fxutils.centerChildren
import fxutils.disableIf
import fxutils.drag.setupDropArea
import fxutils.prompt.DetailPane
import fxutils.styleClass
import javafx.application.Platform
import javafx.beans.property.SimpleDoubleProperty
import javafx.geometry.Point2D
import javafx.geometry.Rectangle2D
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.transform.Scale
import javafx.scene.transform.Translate
import org.kordamp.ikonli.material2.Material2AL
import ponticello.impl.*
import ponticello.model.obj.SampleObject
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.project.UIState
import ponticello.model.score.*
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.*
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.ParameterType
import ponticello.sc.view.ObjectSelectorControl
import ponticello.ui.controls.InlineParameterControlsBar
import ponticello.ui.dock.AppLayout
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.registry.InstrumentRegistryPane
import ponticello.ui.registry.SearchableParameterDefListView
import reaktive.Observer
import reaktive.value.*
import reaktive.value.binding.*
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asReactiveValue

class SoundProcessView(
    override val obj: SoundProcess, instance: ScoreObjectInstance,
) : ScoreObjectView(instance), ParameterControlList.Listener {
    private var spectrogramImage: Image? = null
    private val spectrogramSegments = mutableListOf<SpectrogramSegment>()

    private var startPosObserver: Observer? = null
    private var rateObserver: Observer? = null
    private var sampleObserver: Observer? = null
    private var sampleDisplayObserver: Observer? = null
    private var sampleContentObserver: Observer? = null

    private val objectPane = Pane()
    private val observers = mutableMapOf<ParameterControl, Observer>()
    private val envelopeEditors = mutableListOf<EnvelopeEditor>()
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
        obj.controls.addListener(this)
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

        sampleObserver = observeSample()
        sampleDisplayObserver = obj.displaySample?.forEach { updateSpectrogram() }

        if (!parentPane.isRoot(obj)) {
            val synthDefSelector = ObjectSelectorControl(obj.instrumentSelector)
            inlineControls.children.add(1, synthDefSelector)
            val inlineControlsBar = InlineParameterControlsBar(obj.controls, this)
            inlineControls.children.add(2, inlineControlsBar)
        }
    }

    private fun observeSample(): Observer = obj.sample.forEach { s ->
        sampleContentObserver?.kill()
        if (s != null) {
            val sample = s.get()
            if (sample is SampleObject) {
                sampleContentObserver = sample.contentsChanged.observe { _ -> updateSpectrogram() }
            }
            updateSpectrogram()
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
            possibleParameters, "New parameter", obj, fixedParameterType = ParameterType.Numerical
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

    private fun addedEnvelopeControl(control: NamedParameterControl, env: EnvelopeControl) {
        if (env.display.now) displayEnvelope(control, env.points)
        observers[env] = env.display.observe { _, _, display ->
            if (display) displayEnvelope(control, env.points)
            else removeEnvelope(control)
        }
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

    override fun added(obj: NamedParameterControl, idx: Int) {
        when (val ctrl = obj.now) {
            is EnvelopeControl -> addedEnvelopeControl(obj, ctrl)
            else -> {}
        }
        val ctrl = obj.now
        if (ctrl !is ValueControl) return
        addedConstantControl(obj, ctrl)
    }

    private fun addedConstantControl(
        control: NamedParameterControl,
        ctrl: ValueControl,
    ) {
        when (control.name.now) {
            "startPos" -> startPosObserver = ctrl.value.forEach { _ -> displaySpectrogram() }
            "rate" -> rateObserver = ctrl.value.forEach { _ -> displaySpectrogram() }
        }
    }

    override fun removed(obj: NamedParameterControl) {
        observers.remove(obj.now)?.kill()
        when (obj.now) {
            is EnvelopeControl -> removeEnvelope(obj)
            else -> {}
        }
        if (obj.now !is ValueControl) return
        removedConstantControl(obj)
    }

    private fun removedConstantControl(control: NamedParameterControl) {
        when (control.name.now) {
            "startPos" -> {
                startPosObserver?.kill()
                displaySpectrogram()
            }

            "rate" -> {
                rateObserver?.kill()
                displaySpectrogram()
            }
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
        if (oldControl is BufferControl && parameter.name.now == "buf") {
            sampleObserver?.kill()
            updateSpectrogram()
        }
        if (oldControl is ValueControl) {
            when (parameter.name.now) {
                "startPos" -> startPosObserver?.kill()
                "rate" -> rateObserver?.kill()
            }
        }
        if (newControl is BufferControl && parameter.name.now == "buf") {
            sampleObserver = observeSample()
        }
        if (newControl is ValueControl) addedConstantControl(parameter, newControl)
    }

    override fun rescale() {
        super.rescale()
        for (e in envelopeEditors) {
            if (e.namedControl.spec.now is NumericalControlSpec) {
                e.repaint()
            }
        }
        rescaleSpectrogram()
    }

    override fun resizedObject(obj: ScoreObject) {
        super.resizedObject(obj)
        displaySpectrogram()
    }

    private fun updateSpectrogram() {
        Platform.runLater {
            objectPane.children.removeAll(spectrogramSegments.flatMap { seg -> seg.nodes() })
            spectrogramSegments.clear()
            if (obj.displaySample?.now != true) return@runLater
            val sample = obj.sample.now?.get()
            if (sample !is SampleObject) return@runLater
            spectrogramImage = sample.spectrogramImage
            displaySpectrogram()
        }
    }

    private fun rescaleSpectrogram() {
        for (node in spectrogramSegments) {
            val x = getWidth(node.start)
            node.image.layoutX = x
            node.image.fitWidth = getWidth(node.duration)
            node.image.fitHeight = objectPane.height
            if (obj.playBufRate!!.now < zero) {
                node.image.transforms.setAll(
                    Translate(node.image.fitWidth, 0.0),
                    Scale(-1.0, 1.0)
                )
            }
            node.loopPointIndicator?.startX = x
            node.loopPointIndicator?.endX = x
            node.loopPointIndicator?.endY = objectPane.height
        }
    }

    private fun displaySpectrogram() = Platform.runLater {
        objectPane.children.removeAll(spectrogramSegments.flatMap { seg -> seg.nodes() })
        spectrogramSegments.clear()
        if (obj.displaySample?.now != true) return@runLater
        if (spectrogramImage == null) return@runLater
        val sample = obj.sample.now?.get() as? SampleObject ?: return@runLater
        val rate = obj.playBufRate?.now ?: one(precision = 3)
        if (rate == zero) return@runLater
        val sampleDuration = sample.duration().now
        val defaultStartPos = if (rate < zero) sampleDuration else zero
        var startPos = obj.playbufStartPos?.now?.wrapAt(sampleDuration) ?: defaultStartPos
        if (rate < zero && startPos == zero) startPos = sampleDuration
        if (rate > zero && startPos == sampleDuration) startPos = zero
        var t = zero
        while (true) {
            if (t >= obj.duration) break
            var dur = when {
                t > zero -> sampleDuration / rate.abs()
                rate > zero -> (sampleDuration - startPos) / rate
                else -> if (startPos != zero) startPos / -rate else sample.duration.now / -rate
            }
            if (dur == zero) break
            if (t + dur > obj.duration) dur = obj.duration - t
            val view = displaySpectrogramPart(
                dur, sampleDuration, rate,
                startPos = if (t != zero) defaultStartPos else startPos
            )
            view.layoutX = getWidth(t)
            var loopPointIndicator: Line? = null
            if (spectrogramSegments.isNotEmpty()) {
                loopPointIndicator = Line(view.layoutX, 0.0, view.layoutX, objectPane.height)
                loopPointIndicator.stroke = Color.WHITE
                loopPointIndicator.viewOrder = 500.0
            }
            spectrogramSegments.add(SpectrogramSegment(t, dur, view, loopPointIndicator))
            t += dur
        }
        objectPane.children.addAll(spectrogramSegments.map { eg -> eg.image })
        objectPane.children.addAll(spectrogramSegments.mapNotNull { seg -> seg.loopPointIndicator })
    }

    private fun displaySpectrogramPart(
        duration: Decimal,
        sampleDuration: Decimal,
        rate: Decimal,
        startPos: Decimal,
    ): ImageView {
        val view = ImageView(spectrogramImage)
        val pixelsPerSecond = (spectrogramImage!!.width / sampleDuration * rate.absoluteValue).toDouble()
        var minX = ((spectrogramImage!!.width / sampleDuration) * startPos).toDouble()
        val minY = 0.0
        val width = (pixelsPerSecond * duration).toDouble()
        if (rate < zero) minX -= width
        val height = spectrogramImage!!.height
        view.viewport = Rectangle2D(minX, minY, width, height)
        view.fitHeight = objectPane.height
        view.fitWidth = getWidth(duration)
        view.viewOrder = 1000.0
        if (rate < zero) view.transforms.addAll(
            Translate(view.fitWidth, 0.0),
            Scale(-1.0, 1.0),
        )
        return view
    }

    private data class SpectrogramSegment(
        val start: Decimal,
        val duration: Decimal,
        val image: ImageView,
        val loopPointIndicator: Line?,
    ) {
        fun nodes() = if (loopPointIndicator != null) listOf(image, loopPointIndicator) else listOf(image)
    }
}