package xenakis.ui.score

import fxutils.format
import fxutils.setBackground
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.layout.BorderPane
import javafx.scene.paint.Color
import javafx.stage.Stage
import xenakis.impl.toDecimal
import xenakis.sc.*
import kotlin.math.roundToInt

class LFOCanvas : Canvas() {
    private var lfo: LFO? = null
    private var spec: NumericalControlSpec? = null
    private var duration: Double? = null

    init {
        widthProperty().addListener { _ -> paint() }
        heightProperty().addListener { _ -> paint() }
    }

    fun display(lfo: LFO, spec: NumericalControlSpec, duration: Double) {
        this.lfo = lfo
        this.spec = spec
        this.duration = duration
        paint()
    }

    private fun paint() = with(graphicsContext2D) {
        if (duration == null || spec == null || lfo == null) return
        if (width <= 0.0 || height <= 0.0) return
        val n = width.toInt()
        val sampleRate = (n / duration!!).roundToInt()
        val values = DoubleArray(n)
        lfo!!.generateValues(duration!!, sampleRate, values)
        println(values.map { v -> v.format(2) })
        paint(values)
    }

    //TODO paint only what is shown on the screen!
    private fun paint(values: DoubleArray) = with(graphicsContext2D) {
        clearRect(0.0, 0.0, width, height)
        stroke = spec!!.associatedColor
        lineWidth = 1.0
        var x = 0.0
        beginPath()
        val transform = spec!!.mapOnto(height..0.0)
        for (value in values) {
            val y = transform.map(value)
            lineTo(x, y)
            x += 1.0
        }
        stroke()
    }
}

class App : Application() {
    override fun start(primaryStage: Stage) {
        val canvas = LFOCanvas()
        var lfo: LFO = LinRange(Sine(ConstantLFO(1.0), 0.0), 0.1, 0.3)
        lfo = Line(0.1, 3.0)
        lfo = Sine(lfo, initialPhase = 0.0)
        val spec = NumericalControlSpec(0.0, lfo.min, lfo.max, 0.01.toDecimal(), Warp.Linear, Color.GREEN)
        canvas.display(lfo, spec, duration = 10.0)
        val root = BorderPane(canvas)
        canvas.widthProperty().bind(root.widthProperty())
        canvas.heightProperty().bind(root.heightProperty())
        val scene = Scene(root)
        root.setBackground(Color.BLACK)
        root.setPrefSize(800.0, 600.0)

        primaryStage.title = "LFO"
        primaryStage.scene = scene
        primaryStage.show()

    }
}

fun main() {
    Application.launch(App::class.java)
}