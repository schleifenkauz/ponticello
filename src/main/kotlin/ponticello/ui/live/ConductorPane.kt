package ponticello.ui.live

import fxutils.*
import fxutils.actions.action
import fxutils.actions.makeButton
import fxutils.actions.registerShortcuts
import fxutils.controls.CheckBox
import fxutils.controls.IntSpinner
import fxutils.drag.setupDropArea
import fxutils.prompt.SelectorPrompt
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
import javafx.scene.text.Font
import javafx.util.Duration
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2MZ
import org.kordamp.ikonli.materialdesign2.MaterialDesignF
import ponticello.impl.*
import ponticello.model.player.Conductor
import ponticello.model.player.GRUConductor
import ponticello.model.project.PonticelloProject
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.impl.DecimalSpinner
import ponticello.ui.launcher.PonticelloMainActivity
import ponticello.ui.score.RootScorePane
import reaktive.Observer
import reaktive.value.binding.equalTo
import reaktive.value.binding.`if`
import reaktive.value.binding.map
import reaktive.value.binding.or
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asProperty
import reaktive.value.now
import java.io.File

class ConductorPane(
    private val conductor: Conductor,
    private val scorePane: RootScorePane
) : ToolPane(), Conductor.View {
    override val type: Type get() = ConductorPane

    private var conductorTime: Decimal = zero

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

    private val minWarpSpinner = DecimalSpinner(
        conductor.options.minWarp,
        min = 0.2.toDecimal(), max = 1.toDecimal(), step = 0.05.toDecimal()
    ).minColumns(5)

    private val maxWarpSpinner = DecimalSpinner(
        conductor.options.maxWarp,
        min = 1.toDecimal(), max = 2.0.toDecimal(), step = 0.05.toDecimal()
    ).minColumns(5)

    private val minBeatDurSpinner = DecimalSpinner(
        conductor.options.minBeatDur,
        min = 0.1.toDecimal(), max = 2.toDecimal(), step = 0.1.toDecimal()
    ).minColumns(5)

    private val visualFeedbackCheckBox = CheckBox(conductor.options.visualFeedback)
    private val saveTempoCurveCheckBox = CheckBox(conductor.options.saveTempoCurve)

    private val extraOptionsField = textField(conductor.options.extraArguments.now) styleClass "sleek-text-field"

    private val modelSelector = ModelSelector().selectorButton(conductor.options.modelName)

    private val videoInputField = textField(conductor.options.videoInput.now) styleClass "sleek-text-field"

    private val configControls = listOf(portSpinner, countdownTimeSpinner, extraOptionsField, modelSelector, videoInputField)

    private val toggleButton = startStopAction.withContext(this).makeButton("large-icon-button")
    private val countdownIndicator = ProgressBar() styleClass "conductor-countdown"
    private val centerPane = StackPane(toggleButton)
    private val barPositionLabel = Label("Beat: - / 0")
    private val currentMeasureLabel = Label("Measure: 0")
    private val conductorTimeIndicator = Line().styleClass("play-head", "conductor-time")

    private val scoreRepaintObserver: Observer

    private val videoInputProperty = conductor.options.videoInput.asProperty()
    private val extraOptionsProperty = conductor.options.extraArguments.asProperty()

    override val content: VBox

    override fun defaultState(): ToolPaneState = ToolPaneState.docked

    init {
        styleClass("conductor-view")
        conductor.addView(this)
        conductorTimeIndicator.endYProperty().bind(scorePane.heightProperty())
        StackPane.setAlignment(toggleButton, Pos.CENTER)
        StackPane.setAlignment(countdownIndicator, Pos.CENTER)
        barPositionLabel.font = Font(18.0)
        currentMeasureLabel.font = Font(18.0)
        countdownIndicator.prefHeight = 30.0
        countdownIndicator.pad(5.0)
        countdownIndicator.maxWidth = Double.POSITIVE_INFINITY
        extraOptionsField.prefWidth = 400.0
        videoInputProperty.bindBidirectional(videoInputField.textProperty())
        extraOptionsProperty.bindBidirectional(extraOptionsField.textProperty())
        content = VBox(
            HBox(5.0, Label("Port:            "), portSpinner).centerChildren(),
            HBox(5.0, Label("Countdown:       "), countdownTimeSpinner).centerChildren(),
            HBox(5.0, Label("Threshold:       "), beatThresholdSpinner).centerChildren(),
            HBox(5.0, Label("Factor:          "), warpFactorSpinner).centerChildren(),
            HBox(5.0, Label("Min interval:    "), minBeatDurSpinner).centerChildren(),
            HBox(5.0, Label("Warp range:      "), minWarpSpinner, Label("-"), maxWarpSpinner),
            HBox(5.0, Label("Blink:           "), visualFeedbackCheckBox),
            HBox(5.0, Label("Save Tempo Curve:"), saveTempoCurveCheckBox),
            HBox(5.0, Label("Model:           "), modelSelector.alwaysHGrow()).centerChildren(),
            HBox(5.0, Label("Input:           "), videoInputField.alwaysHGrow()).centerChildren(),
            Label("Command line options:      "),
            extraOptionsField,
            centerPane,
            HBox(10.0, currentMeasureLabel, barPositionLabel).center()
        ).pad(5.0)
        toggleButton.disableProperty().bind(
            conductor.options.modelName.equalTo("<none>")
                .or(conductor.options.videoInput.map(String::isBlank))
                .asObservableValue()
        )
        registerShortcuts(listOf(startStopAction.withContext(this)))
        scoreRepaintObserver = scorePane.onRepaint.observe { _ -> repositionConductorTimeIndicator() }
        setupDropArea {
            handleSingleFile("mp4", "webm", "mkv") { _, file ->
                conductor.options.videoInput.set(file.absolutePath)
                true
            }
        }
    }

    override fun onScheduled(startTime: Decimal) = Platform.runLater {
        conductorTime = startTime
        if (conductorTimeIndicator !in scorePane.children) {
            scorePane.children.add(conductorTimeIndicator)
        }
        repositionConductorTimeIndicator()
        centerPane.children.setAll(countdownIndicator)
        for (ctrl in configControls) {
            ctrl.isDisable = true
        }
        val totalMs = countdownTimeSpinner.value.get() * 1000.0
        barPositionLabel.text = "Beat: 0 / ${conductor.beatsPerBar}"
        CountdownTimer(countdownIndicator, totalMs).start()
    }

    override fun onStarted() = Platform.runLater {
        centerPane.children.setAll(toggleButton)
    }

    override fun onBeat(measure: Int, barPosition: Int, conductorTime: Decimal) = Platform.runLater {
        this.conductorTime = conductorTime
        if (conductor.options.visualFeedback.now) {
            background = background(Color.RED)
            val pause = PauseTransition(Duration.millis(100.0))
            pause.setOnFinished { background = null }
            pause.play()
        }
        currentMeasureLabel.text = "Measure: $measure"
        barPositionLabel.text = "Beat: $barPosition / ${conductor.beatsPerBar}"
        repositionConductorTimeIndicator()
    }

    private fun repositionConductorTimeIndicator() {
        conductorTimeIndicator.startX = scorePane.getX(conductorTime)
        conductorTimeIndicator.endX = conductorTimeIndicator.startX
    }

    override fun onStopped() = Platform.runLater {
        barPositionLabel.text = "Beat: - / 0"
        currentMeasureLabel.text = "Measure: 0"
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

    private class ModelSelector : SelectorPrompt<String>("Select model") {
        override fun extractText(option: String): String = option

        override fun options(): List<String> =
            File(System.getProperty("user.home"), "dev/rubato/models").listFiles()!!
                .filter { f -> f.extension == "pth" }
                .map { f -> f.nameWithoutExtension }
    }

    companion object : Type(uid = 19, "Conductor Sync") {
        override val defaultSide: Side get() = Side.LEFT

        override val icon: Ikon get() = MaterialDesignF.FACE

        override fun createToolPane(project: PonticelloProject): ToolPane {
            val conductor = GRUConductor.get(project.context)
            val scorePane = project.context[PonticelloMainActivity].mainScoreView
            return ConductorPane(conductor, scorePane)
        }

        private val startStopAction = action<ConductorPane>("Start/stop") {
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
                    if (!v.conductor.start()) {
                        Logger.error("Failed to start live beat detection", Logger.Category.Playback)
                    }
                }
            }
        }
    }
}