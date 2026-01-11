package ponticello.ui.misc

import fxutils.centerChildren
import fxutils.controls.CheckBox
import fxutils.infiniteSpace
import fxutils.prompt.ConfirmablePrompt
import fxutils.styleClass
import fxutils.vspace
import javafx.scene.Parent
import javafx.scene.control.Label
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import ponticello.impl.Decimal
import ponticello.impl.copy
import ponticello.impl.toDecimal
import ponticello.model.PlaybackSettings
import ponticello.sc.NumericalControlSpec
import ponticello.ui.controls.Knob
import reaktive.value.ReactiveVariable
import reaktive.value.now

class PlaybackSettingsPrompt(
    private val settings: PlaybackSettings,
) : ConfirmablePrompt<Unit>("Playback settings", confirmText = "_Apply") {
    private val scLangLatency = settings.scLangLatency.copy()
    private val serverLatency = settings.serverLatency.copy()
    private val extraLatency = settings.extraLatency.copy()
    private val logScCode = settings.logScCode.copy()
    private val scrollWithPlayHead = settings.scrollWithPlayHead.copy()
    private val djMode = settings.djMode.activated.copy()

    override val content: Parent = VBox(
        BorderPane(Label("Latency").styleClass("sub-heading")),
        latencyKnobsPane(scLangLatency, serverLatency, extraLatency),
        vspace(10.0),
        HBox(infiniteSpace(), CheckBox(logScCode, "Log SuperCollider code: "), infiniteSpace()).centerChildren(),
        HBox(
            infiniteSpace(),
            CheckBox(scrollWithPlayHead, "Scroll with play-head: "),
            infiniteSpace()
        ).centerChildren(),
        HBox(infiniteSpace(), CheckBox(djMode, "DJ mode: "), infiniteSpace()).centerChildren(),
    )

    override fun confirm() {
        settings.scLangLatency.set(scLangLatency.now)
        settings.serverLatency.set(serverLatency.now)
        settings.extraLatency.set(extraLatency.now)
        settings.logScCode.set(logScCode.now)
        settings.scrollWithPlayHead.set(scrollWithPlayHead.now)
        settings.djMode.activated.set(djMode.now)
    }

    companion object {
        fun latencyKnobsPane(
            scLangLatency: ReactiveVariable<Decimal>,
            serverLatency: ReactiveVariable<Decimal>,
            extraLatency: ReactiveVariable<Decimal>,
        ): HBox {
            val pane = HBox(5.0)
            for ((name, description, variable) in listOf(
                Triple("sclang", "Latency (sclang) in ms", scLangLatency),
                Triple("scsynth", "Latency (scsynth) in ms", serverLatency),
                Triple("extra", "Extra latency (ms)", extraLatency)
            )) {
                val knob = Knob(description, variable, NumericalControlSpec(0.1, 0.0, 1.0, 0.01.toDecimal()))
                val box = VBox(5.0, Label(name), knob) styleClass "latency-knob-box"
                pane.children.add(box)
            }
            return pane
        }
    }
}