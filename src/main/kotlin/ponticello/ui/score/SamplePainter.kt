package ponticello.ui.score

import fxutils.styleClass
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.geometry.Rectangle2D
import javafx.scene.canvas.Canvas
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.scene.transform.Scale
import javafx.scene.transform.Translate
import ponticello.impl.*
import ponticello.model.score.SoundProcess
import ponticello.model.score.controls.BufferControl
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ParameterControlList
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl
import ponticello.model.score.controls.ValueControl
import ponticello.model.server.SampleObject
import ponticello.ui.score.ScoreObjectView.Companion.MAX_OBJECT_WIDTH
import reaktive.Observer
import reaktive.dependencies
import reaktive.value.forEach
import reaktive.value.now

class SamplePainter(
    private val view: SoundProcessView,
    private val objectPane: Pane,
) : ParameterControlList.Listener {
    private val obj: SoundProcess = view.obj

    private var spectrogramImage: Image? = null
    private val spectrogramSegments = mutableListOf<SpectrogramSegment>()
    val gridCanvas: Canvas = Canvas()
    val marker: Line = Line() styleClass "grid-marker-line"

    private var startPosObserver: Observer? = null
    private var rateObserver: Observer? = null
    private var sampleObserver: Observer? = null
    private var sampleDisplayObserver: Observer? = null
    private var sampleContentObserver: Observer? = null
    private var sampleGridObserver: Observer? = null

    fun initialize() {
        sampleObserver = observeSample()
        sampleDisplayObserver = obj.displaySample?.forEach { updateSpectrogram() }
        gridCanvas.widthProperty().bind(Bindings.min(view.prefWidthProperty(), MAX_OBJECT_WIDTH))
        gridCanvas.heightProperty().bind(Bindings.min(view.prefHeightProperty(), TempoGrid.GRID_HEIGHT))
        gridCanvas.layoutYProperty().bind(objectPane.heightProperty().subtract(gridCanvas.heightProperty()))
        marker.startYProperty().bind(objectPane.heightProperty().subtract(gridCanvas.heightProperty()))
        marker.endYProperty().bind(objectPane.heightProperty())
        objectPane.children.addAll(gridCanvas, marker)
        obj.controls.addListener(this)
    }

    private fun observeSample(): Observer = obj.sample.forEach { s ->
        sampleContentObserver?.kill()
        sampleContentObserver = null
        sampleGridObserver?.kill()
        sampleGridObserver = null
        if (s != null) {
            val sample = s.get()
            if (sample is SampleObject) {
                sampleContentObserver = sample.contentsChanged.observe { _ ->
                    updateSpectrogram()
                }
                val meter = sample.meter
                sampleGridObserver = dependencies(
                    meter.beatsPerMinute, meter.beatsPerBar, meter.ticksPerBeat,
                    sample.firstBeat
                ).observe {
                    redrawGrid()
                }
            }
            redrawGrid()
            updateSpectrogram()
        }
    }

    override fun added(obj: NamedParameterControl, idx: Int) {
        val ctrl = obj.now
        if (ctrl is ValueControl) {
            addedConstantControl(obj, ctrl)
        }
    }

    override fun removed(obj: NamedParameterControl, idx: Int) {
        if (obj.now is ValueControl) {
            removedConstantControl(obj)
        }
    }

    private fun removedConstantControl(control: NamedParameterControl) {
        when (control.name.now) {
            "startPos" -> {
                startPosObserver?.kill()
                displaySpectrogram()
                redrawGrid()
            }

            "rate" -> {
                rateObserver?.kill()
                displaySpectrogram()
                redrawGrid()
            }
        }
    }

    override fun reassignedControl(
        parameter: NamedParameterControl, oldControl: ParameterControl, newControl: ParameterControl,
    ) {
        if (oldControl is BufferControl && parameter.name.now == "buf") {
            sampleObserver?.kill()
            updateSpectrogram()
            clearGrid()
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
            "startPos" -> startPosObserver = ctrl.value.forEach { _ ->
                displaySpectrogram()
                redrawGrid()
            }

            "rate" -> rateObserver = ctrl.value.forEach { _ ->
                displaySpectrogram()
                redrawGrid()
            }
        }
    }


    private fun updateSpectrogram() {
        Platform.runLater {
            objectPane.children.removeAll(spectrogramSegments.flatMap { seg -> seg.nodes() })
            spectrogramSegments.clear()
            if (obj.displaySample?.now != true) return@runLater
            val sample = obj.sample.now?.get()
            if (sample !is SampleObject || !sample.infosUpdated) return@runLater
            spectrogramImage = sample.spectrogramImage.now
            displaySpectrogram()
        }
    }

    fun displaySpectrogram() = Platform.runLater {
        if (spectrogramImage == null) return@runLater
        objectPane.children.removeAll(spectrogramSegments.flatMap { seg -> seg.nodes() })
        spectrogramSegments.clear()
        if (obj.displaySample?.now != true) return@runLater
        if (spectrogramImage == null) return@runLater
        val sample = obj.sample.now?.get() as? SampleObject ?: return@runLater
        val rate = obj.playBufRate?.now ?: one(precision = 3)
        if (rate == zero) return@runLater
        val sampleDuration = sample.duration().now
        if (sampleDuration <= zero) return@runLater
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
            view.layoutX = this.view.getWidth(t)
            var loopPointIndicator: Line? = null
            if (spectrogramSegments.isNotEmpty()) {
                loopPointIndicator = Line(view.layoutX, 0.0, view.layoutX, objectPane.height)
                loopPointIndicator.stroke = Color.WHITE
                loopPointIndicator.viewOrder = 500.0
            }
            spectrogramSegments.add(SpectrogramSegment(t, dur, view, view.viewport, loopPointIndicator))
            t += dur
        }
        objectPane.children.addAll(spectrogramSegments.map { eg -> eg.image })
        objectPane.children.addAll(spectrogramSegments.mapNotNull { seg -> seg.loopPointIndicator })
    }

    private fun rescaleSpectrogram() {
        for (segment in spectrogramSegments) {
            val x = view.getWidth(segment.start)
            segment.image.layoutX = x
            val width = view.getWidth(segment.duration)
            val layoutX = x + view.layoutX
            if (width > MAX_OBJECT_WIDTH && layoutX < 0.0) {
                segment.image.fitWidth = MAX_OBJECT_WIDTH
                val offsetX = -layoutX
                segment.image.translateX = offsetX
                val viewportWidth = segment.viewport.width * (MAX_OBJECT_WIDTH / width)
                val imageWidth = segment.image.image.width
                val viewportOffset = offsetX * (imageWidth / width)
                segment.image.viewport = Rectangle2D(
                    segment.viewport.minX + viewportOffset, segment.viewport.minY,
                    viewportWidth, segment.viewport.height
                )
            } else {
                segment.image.fitWidth = width
                segment.image.translateX = 0.0
                segment.image.viewport = segment.viewport
            }
            segment.image.fitHeight = objectPane.height
            val rate = obj.playBufRate
            if (rate != null && rate.now < zero) {
                segment.image.transforms.setAll(
                    Translate(segment.image.fitWidth, 0.0),
                    Scale(-1.0, 1.0)
                )
            }
            segment.loopPointIndicator?.startX = x
            segment.loopPointIndicator?.endX = x
            segment.loopPointIndicator?.endY = objectPane.height
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
        view.fitWidth = this.view.getWidth(duration)
        view.viewOrder = 1000.0
        if (rate < zero) view.transforms.addAll(
            Translate(view.fitWidth, 0.0),
            Scale(-1.0, 1.0),
        )
        return view
    }

    fun rescale() {
        rescaleSpectrogram()
        redrawGrid()
    }

    fun relocated() {
        if (view.prefWidth > MAX_OBJECT_WIDTH) {
            redrawGrid()
            rescaleSpectrogram()
        }
    }

    private data class SpectrogramSegment(
        val start: Decimal,
        val duration: Decimal,
        val image: ImageView,
        val viewport: Rectangle2D,
        val loopPointIndicator: Line?,
    ) {
        fun nodes() = if (loopPointIndicator != null) listOf(image, loopPointIndicator) else listOf(image)
    }

    private fun clearGrid() {
        gridCanvas.graphicsContext2D.clearRect(0.0, 0.0, gridCanvas.width, gridCanvas.height)
    }

    private fun redrawGrid() {
        clearGrid()
        val grid = view.tempoGrid
        if (grid == null) {
            marker.visibleProperty().unbind()
            marker.isVisible = false
            return
        }
        val offsetX = if (view.prefWidth > MAX_OBJECT_WIDTH && view.layoutX < 0.0) -view.layoutX else 0.0
        gridCanvas.translateX = offsetX
        val offsetDur = view.getDuration(offsetX)
        grid.paintGrid(view.parentPane.pixelsPerSecond, offsetDur)
    }
}