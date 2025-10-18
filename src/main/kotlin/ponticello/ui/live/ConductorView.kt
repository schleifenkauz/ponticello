package ponticello.ui.live

import fxutils.*
import fxutils.actions.action
import fxutils.actions.makeButton
import fxutils.actions.registerShortcuts
import fxutils.controls.IntSpinner
import hextant.context.Context
import javafx.animation.AnimationTimer
import javafx.animation.PauseTransition
import javafx.application.Platform
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.control.ProgressBar
import javafx.scene.layout.HBox
import javafx.scene.layout.StackPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import javafx.util.Duration
import org.kordamp.ikonli.material2.Material2MZ
import ponticello.impl.*
import ponticello.model.player.Conductor
import ponticello.model.player.GRUConductor
import ponticello.ui.impl.DecimalSpinner
import ponticello.ui.impl.makeSubWindow
import ponticello.ui.launcher.PonticelloMainActivity
import ponticello.ui.score.ScorePane
import reaktive.value.binding.`if`
import reaktive.value.now

class ConductorView(
    private val conductor: Conductor,
    private val scorePane: ScorePane
) : VBox(5.0), Conductor.View {
    private val portSpinner = IntSpinner(conductor.options.port, 1024..65535).minColumns(5)
    private val countdownTimeSpinner = IntSpinner(conductor.options.countdownTime, 1, 20).minColumns(5)
    private val beatThresholdSpinner = DecimalSpinner(
        conductor.options.beatThreshold,
        min = zero, max = one, step = 0.01.toDecimal()
    ).minColumns(5)
    private val warpFactorSpinner = DecimalSpinner(
        conductor.options.warpFactor,
        min = zero, max = 2.toDecimal(), step = 0.05.toDecimal()
    ).minColumns(5)
    private val extraOptionsField = textField(conductor.options.extraArguments.now) {
        setOnAction {
            conductor.options.extraArguments.set(text)
        }
    } styleClass "sleek-text-field"

    private val configControls = listOf(portSpinner, countdownTimeSpinner, extraOptionsField)

    private val toggleButton = startStopAction.withContext(this).makeButton("large-icon-button")
    private val countdownIndicator = ProgressBar() styleClass "conductor-countdown"
    private val centerPane = StackPane(toggleButton)
    private val barPositionLabel = Label("- / 0")
    private val conductorTimeIndicator = Line().styleClass("play-head", "conductor-time")

    init {
        conductor.addView(this)
        conductorTimeIndicator.endYProperty().bind(scorePane.heightProperty())
        StackPane.setAlignment(toggleButton, Pos.CENTER)
        StackPane.setAlignment(countdownIndicator, Pos.CENTER)
        countdownIndicator.prefHeight = 30.0
        countdownIndicator.pad(5.0)
        countdownIndicator.maxWidth = Double.POSITIVE_INFINITY
        extraOptionsField.prefWidth = 400.0
        children.addAll(
            HBox(5.0, Label("Port:     "), portSpinner).centerChildren(),
            HBox(5.0, Label("Countdown:"), countdownTimeSpinner).centerChildren(),
            HBox(5.0, Label("Threshold:"), beatThresholdSpinner).centerChildren(),
            HBox(5.0, Label("Factor   :"), warpFactorSpinner).centerChildren(),
            Label("Options:  "),
            extraOptionsField,
            centerPane,
            barPositionLabel.centered()
        )
        pad(5.0)
        prefHeight = 140.0

        registerShortcuts(listOf(startStopAction.withContext(this)))
    }

    override fun onScheduled() = Platform.runLater {
        if (conductorTimeIndicator !in scorePane.children) {
            scorePane.children.add(conductorTimeIndicator)
            conductorTimeIndicator.startX = 0.0
            conductorTimeIndicator.endX = 0.0
        }
        centerPane.children.setAll(countdownIndicator)
        for (ctrl in configControls) {
            ctrl.isDisable = true
        }
        val totalMs = countdownTimeSpinner.value.get() * 1000.0
        CountdownTimer(countdownIndicator, totalMs).start()
    }

    override fun onStarted() = Platform.runLater {
        centerPane.children.setAll(toggleButton)
    }

    override fun onBeat(barPosition: Int, conductorTime: Decimal) = Platform.runLater {
        background = background(Color.RED)
        val pause = PauseTransition(Duration.millis(100.0))
        pause.setOnFinished { background = null }
        pause.play()
        barPositionLabel.text = "$barPosition / ${conductor.beatsPerBar}"
        conductorTimeIndicator.startX = scorePane.getWidth(conductorTime)
        conductorTimeIndicator.endX = conductorTimeIndicator.startX
    }

    override fun onStopped() = Platform.runLater {
        barPositionLabel.text = "- / ${conductor.beatsPerBar}"
        for (ctrl in configControls) {
            ctrl.isDisable = false
        }
        scorePane.children.remove(conductorTimeIndicator)
    }

    private class CountdownTimer(
        private val indicator: ProgressBar,
        private val totalMs: Double
    ) : AnimationTimer() {
        private var startTime: Long = 0L

        override fun start() {
            startTime = System.nanoTime()
            super.start()
        }

        override fun handle(now: Long) {
            val elapsedMs = ((now - startTime) / 1_000_000).toDouble()
            val progress = (elapsedMs / totalMs).coerceAtMost(1.0)
            indicator.progress = progress
            if (progress >= 1.0) {
                stop()
            }
        }
    }

    companion object {
        private val startStopAction = action<ConductorView>("Start/stop") {
            description { v -> `if`(v.conductor.isActive, then = { "Stop" }, otherwise = { "Start" }) }
            icon { v ->
                `if`(
                    v.conductor.isActive,
                    then = { Material2MZ.STOP },
                    otherwise = { Material2MZ.PLAY_ARROW })
            }
            shortcut("Ctrl+SPACE")
            executes { v ->
                if (v.conductor.isActive.now) v.conductor.stop()
                else {
                    v.conductor.options.extraArguments.set(v.extraOptionsField.text)
                    if (!v.conductor.start()) {
                        Logger.error("Failed to start live beat detection", Logger.Category.Playback)
                    }
                }
            }
        }

        fun showWindow(context: Context) {
            val conductor = GRUConductor.get(context)
            val scorePane = context[PonticelloMainActivity].mainScoreView
            val view = ConductorView(conductor, scorePane)
            val window = makeSubWindow(view, "Conductor", context)
            window.show()
            window.setOnHidden { conductor.stop() }
        }
    }
}