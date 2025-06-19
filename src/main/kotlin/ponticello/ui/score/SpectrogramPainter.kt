package ponticello.ui.score

import javafx.application.Platform
import javafx.geometry.Rectangle2D
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.transform.Scale
import javafx.scene.transform.Translate
import ponticello.impl.*
import ponticello.model.obj.SampleObject
import ponticello.model.score.ParameterControlList
import ponticello.model.score.ParameterControlList.NamedParameterControl
import ponticello.model.score.SoundProcess
import ponticello.model.score.controls.BufferControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ValueControl
import reaktive.Observer
import reaktive.value.forEach
import reaktive.value.now

class SpectrogramPainter(
    private val associatedView: SoundProcessView,
    private val obj: SoundProcess,
    private val objectPane: Pane,
): ParameterControlList.Listener {
    private var spectrogramImage: Image? = null
    private val spectrogramSegments = mutableListOf<SpectrogramSegment>()

    private var startPosObserver: Observer? = null
    private var rateObserver: Observer? = null
    private var sampleObserver: Observer? = null
    private var sampleDisplayObserver: Observer? = null
    private var sampleContentObserver: Observer? = null

    fun initialize() {
        sampleObserver = observeSample()
        sampleDisplayObserver = obj.displaySample?.forEach { updateSpectrogram() }
        obj.controls.addListener(this)
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

    override fun added(obj: NamedParameterControl, idx: Int) {
        val ctrl = obj.now
        if (ctrl is ValueControl) {
            addedConstantControl(obj, ctrl)
        }
    }

    override fun removed(obj: NamedParameterControl) {
        if (obj.now is ValueControl) {
            removedConstantControl(obj)
        }
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
        parameter: NamedParameterControl, oldControl: ParameterControl, newControl: ParameterControl,
    ) {
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
        if (newControl is ValueControl) {
            addedConstantControl(parameter, newControl)
        }
    }

    private fun addedConstantControl(control: NamedParameterControl, ctrl: ValueControl) {
        when (control.name.now) {
            "startPos" -> startPosObserver = ctrl.value.forEach { _ -> displaySpectrogram() }
            "rate" -> rateObserver = ctrl.value.forEach { _ -> displaySpectrogram() }
        }
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

    fun displaySpectrogram() = Platform.runLater {
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
            view.layoutX = associatedView.getWidth(t)
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

    fun rescaleSpectrogram() {
        for (node in spectrogramSegments) {
            val x = associatedView.getWidth(node.start)
            node.image.layoutX = x
            node.image.fitWidth = associatedView.getWidth(node.duration)
            node.image.fitHeight = objectPane.height
            val rate = obj.playBufRate
            if (rate != null && rate.now < zero) {
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

    private fun displaySpectrogramPart(
        duration: Decimal, sampleDuration: Decimal,
        rate: Decimal, startPos: Decimal,
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
        view.fitWidth = associatedView.getWidth(duration)
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