package ponticello.ui.score

import bundles.createBundle
import fxutils.actions.button
import fxutils.centerChildren
import fxutils.disableIf
import fxutils.prompt.DetailPane
import fxutils.styleClass
import javafx.application.Platform
import javafx.geometry.Rectangle2D
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.transform.Scale
import javafx.scene.transform.Translate
import org.kordamp.ikonli.material2.Material2AL
import ponticello.impl.*
import ponticello.model.obj.SampleObject
import ponticello.model.score.ParameterControlList
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.SynthObject
import ponticello.model.score.controls.BufferControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ValueControl
import ponticello.sc.view.ObjectSelectorControl
import ponticello.ui.controls.InlineParameterControlsBar
import ponticello.ui.launcher.PonticelloMainActivity
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.binding.flatMap
import reaktive.value.binding.not
import reaktive.value.forEach
import reaktive.value.now
import reaktive.value.reactiveValue

class SynthObjectView(
    override val obj: SynthObject, instance: ScoreObjectInstance,
) : ParameterizedScoreObjectView<SynthObject>(instance), ParameterControlList.Listener {
    private var spectrogramImage: Image? = null
    private val spectrogramSegments = mutableListOf<SpectrogramSegment>()

    private var startPosObserver: Observer? = null
    private var rateObserver: Observer? = null
    private var sampleObserver: Observer? = null
    private var sampleDisplayObserver: Observer? = null
    private var sampleContentObserver: Observer? = null

    override val defaultBackgroundColor: ReactiveValue<Color>
        get() = obj.synthDefSelector.result.flatMap { ref -> ref.get()?.color ?: reactiveValue(Color.GRAY) }

    init {
        styleClass("synth-object")
    }

    override fun initialize() {
        super.initialize()
        sampleObserver = observeSample()
        sampleDisplayObserver = obj.displaySample?.forEach { updateSpectrogram() }
        val synthDefSelector = ObjectSelectorControl(obj.synthDefSelector)
        inlineControls.children.add(1, synthDefSelector)
        val inlineControlsBar = InlineParameterControlsBar(obj.controls, this)
        inlineControls.children.add(2, inlineControlsBar)
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
            context[PonticelloMainActivity].synthDefsPane().listView.showContent(obj.synthDef)
        }.disableIf(obj.synthDefSelector.isResolved.not())
        val box = ObjectSelectorControl(obj.synthDefSelector, createBundle())
        pane.addItem("SynthDef: ", HBox(5.0, box, viewBtn).centerChildren())
        val controlsPane = ParameterControlsPane(obj, "Synth controls", this)
        controlsPane.listView.autoResizeScene = true
        pane.children.add(controlsPane)
    }

    override fun added(obj: ParameterControlList.NamedParameterControl, idx: Int) {
        super<ParameterizedScoreObjectView>.added(obj, idx)
        val ctrl = obj.now
        if (ctrl !is ValueControl) return
        addedConstantControl(obj, ctrl)
    }

    private fun addedConstantControl(
        control: ParameterControlList.NamedParameterControl,
        ctrl: ValueControl,
    ) {
        when (control.name.now) {
            "startPos" -> startPosObserver = ctrl.value.forEach { _ -> displaySpectrogram() }
            "rate" -> rateObserver = ctrl.value.forEach { _ -> displaySpectrogram() }
        }
    }

    override fun removed(obj: ParameterControlList.NamedParameterControl) {
        super<ParameterizedScoreObjectView>.removed(obj)
        if (obj.now !is ValueControl) return
        removedConstantControl(obj)
    }

    private fun removedConstantControl(control: ParameterControlList.NamedParameterControl) {
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
        parameter: ParameterControlList.NamedParameterControl,
        oldControl: ParameterControl,
        newControl: ParameterControl,
    ) {
        super.reassignedControl(parameter, oldControl, newControl)
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