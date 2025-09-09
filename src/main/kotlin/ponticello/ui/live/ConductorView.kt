package ponticello.ui.live

import fxutils.*
import fxutils.actions.action
import fxutils.actions.makeButton
import fxutils.actions.registerShortcuts
import fxutils.controls.IntSpinner
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
import javafx.util.Duration
import org.kordamp.ikonli.material2.Material2MZ
import ponticello.impl.Logger
import ponticello.model.player.Conductor
import ponticello.model.player.ScorePlayer
import ponticello.ui.impl.makeSubWindow
import reaktive.Observer
import reaktive.value.binding.`if`
import reaktive.value.binding.map
import reaktive.value.now

class ConductorView(private val conductor: Conductor) : VBox(5.0) {
    private val portSpinner = IntSpinner(conductor.port, 1024..65535).minColumns(5)
    private val countdownTimeSpinner = IntSpinner(conductor.countdownTime, 1, 20).minColumns(5)
    private val toggleButton = startStopAction.withContext(conductor).makeButton("large-icon-button")
    private val countdownIndicator = ProgressBar()
    private val centerPane = StackPane(toggleButton)
    private val scheduleObserver: Observer
    private val activeObserver: Observer
    private val beatObserver: Observer

    init {
        val barPosition = label(
            conductor.beats.map { beats -> "$beats / ${conductor.beatsPerBar}" }
        )
        StackPane.setAlignment(toggleButton, Pos.CENTER)
        StackPane.setAlignment(countdownIndicator, Pos.CENTER)
        barPosition.isManaged = false
        countdownIndicator.prefHeight = 25.0
        countdownIndicator.pad(10.0)
        countdownIndicator.maxWidth = Double.POSITIVE_INFINITY
        children.addAll(
            HBox(5.0, Label("Port:     "), portSpinner).centerChildren(),
            HBox(5.0, Label("Countdown:"), countdownTimeSpinner).centerChildren(),
            centerPane,
            barPosition.centered()
        )
        pad(5.0)
        prefHeight = 120.0

        scheduleObserver = conductor.isScheduled.observe { _, _, scheduled ->
            Platform.runLater {
                if (scheduled) {
                    centerPane.children.setAll(countdownIndicator)
                    countdownTimeSpinner.isDisable = true
                    portSpinner.isDisable = true
                    startCountdown()
                } else {
                    centerPane.children.setAll(toggleButton)
                    countdownTimeSpinner.isDisable = false
                    portSpinner.isDisable = false
                }
            }
        }

        activeObserver = conductor.isActive.observe { _, _, active ->
            if (active) {
                Platform.runLater {
                    centerPane.children.setAll(toggleButton)
                }
            }
        }

        beatObserver = conductor.onBeat.observe { _ ->
            Platform.runLater {
                background = background(Color.RED)
                val pause = PauseTransition(Duration.millis(100.0))
                pause.setOnFinished { background = null }
                pause.play()
            }
        }

        registerShortcuts(listOf(startStopAction.withContext(conductor)))
    }

    private fun startCountdown() {
        val totalMs = countdownTimeSpinner.value.get() * 1000.0
        CountdownTimer(countdownIndicator, totalMs).start()
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
        private val startStopAction = action<Conductor>("Start/stop") {
            description { c -> `if`(c.isActive, then = { "Stop" }, otherwise = { "Start" }) }
            icon { c -> `if`(c.isActive, then = { Material2MZ.STOP }, otherwise = { Material2MZ.PLAY_ARROW }) }
            shortcut("Ctrl+SPACE")
            executes { c ->
                if (c.isActive.now) c.stop()
                else {
                    if (!c.start()) {
                        Logger.error("Failed to start live beat detection", Logger.Category.Playback)
                    }
                }
            }
        }

        fun showWindow(player: ScorePlayer) {
            val conductor = Conductor.forPlayer(player)
            val view = ConductorView(conductor)
            val window = makeSubWindow(view, "Conductor", player.context)
            window.show()
            window.setOnHidden { conductor.stop() }
        }
    }
}