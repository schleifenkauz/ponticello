package ponticello.ui.score

import javafx.scene.canvas.Canvas
import ponticello.model.score.SoundProcess
import ponticello.sc.LFO
import ponticello.sc.NumericalControlSpec
import ponticello.sc.mapOnto
import kotlin.math.roundToInt

class LFOCanvas(private val obj: SoundProcess) : Canvas() {
    private var lfo: LFO? = null
    private lateinit var spec: NumericalControlSpec
    private val duration: Double get() = obj.duration.value

    init {
        widthProperty().addListener { _ -> repaint() }
        heightProperty().addListener { _ -> repaint() }
    }

    fun display(lfo: LFO, spec: NumericalControlSpec) {
        this.lfo = lfo
        this.spec = spec
        repaint()
    }

    private fun repaint() {
        val lfo = lfo ?: return
        if (width <= 0.0 || height <= 0.0) return
        val n = width.toInt()
        val sampleRate = (n / duration).roundToInt()
        val values = DoubleArray(n)
        lfo.generateValues(duration, sampleRate, values)
        paint(values)
    }

    //TODO paint only what is shown on the screen!
    private fun paint(values: DoubleArray) = with(graphicsContext2D) {
        clearRect(0.0, 0.0, width, height)
        stroke = spec.associatedColor
        lineWidth = 1.0
        var x = 0.0
        beginPath()
        val transform = spec.mapOnto(height,0.0)
        for (value in values) {
            val y = transform.map(value)
            lineTo(x, y)
            x += 1.0
        }
        stroke()
    }
}