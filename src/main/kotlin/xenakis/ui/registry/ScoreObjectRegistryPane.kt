package xenakis.ui.registry

import fxutils.SubWindow
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.controls.SliderBar
import javafx.event.Event
import javafx.geometry.HorizontalDirection
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.input.DataFormat
import javafx.scene.layout.BorderPane
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import org.kordamp.ikonli.materialdesign2.MaterialDesignM
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.binding.not
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.one
import xenakis.impl.toDecimal
import xenakis.impl.zero
import xenakis.model.obj.ParameterizedObject
import xenakis.model.registry.MeterRegistry
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.registry.reference
import xenakis.model.score.ScoreObject
import xenakis.sc.NumericalControlSpec
import xenakis.sc.Warp
import xenakis.ui.impl.Direction
import xenakis.ui.live.QuantizationConfigDialog
import xenakis.ui.midi.ContextualMidiReceiver
import xenakis.ui.midi.ParameterControlsMidiContext
import xenakis.ui.registry.ObjectListView.DisplayMode
import xenakis.ui.score.ScoreObjectViewPane

class ScoreObjectRegistryPane(registry: ScoreObjectRegistry) : ObjectRegistryPane<ScoreObject>(registry) {
    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.SubWindow)

    init {
        setup()
    }

    override fun getItemContent(obj: ScoreObject): List<Node> {
        val spec = NumericalControlSpec(zero, zero, one, 0.01.toDecimal(), zero, Warp.Linear)
        val name = reactiveValue("Y in main score")
        val scoreYSlider = SliderBar(obj.liveConfig.yPosition, name, spec.converter())
        scoreYSlider.prefWidth = 150.0
        return listOf(scoreYSlider)
    }

    override fun configureSubWindow(window: SubWindow, obj: ScoreObject) {
        if (obj is ParameterizedObject) {
            registry.context[ContextualMidiReceiver].registerMidiContext(window) {
                ParameterControlsMidiContext(obj.controls)
            }
        }
    }

    override fun getContent(obj: ScoreObject, mode: DisplayMode): Parent {
        val pane = ScoreObjectViewPane.getPane(obj)
        pane.setDefaultSize()
        return BorderPane(pane)
    }

    override fun getActions(box: ObjectBox<ScoreObject>): List<ContextualizedAction> {
        return actions.withContext(box.obj)
    }

    override fun dataFormat(obj: ScoreObject): DataFormat = ScoreObject.DATA_FORMAT

    override fun createNewObject(name: String, ev: Event?): ScoreObject? = null

    companion object {
        val actions = collectActions<ScoreObject> {
            addAction("Toggle looping") {
                applicableIf { obj -> obj.affectsPlayback }
                toggles(
                    { obj -> obj.liveConfig.loop },
                    whenFalse = MaterialDesignR.REPEAT_OFF,
                    whenTrue = MaterialDesignR.REPEAT,
                )
            }
//            addAction("Toggle add to score") {
//                applicableIf { obj -> obj.affectsPlayback }
//                icon(MaterialDesignP.PROGRESS_QUESTION)
//                toggles({ obj -> obj.liveConfig.addToScore })
//            }
            addAction("Quantize start") {
                applicableIf { obj -> obj.affectsPlayback }
                icon(MaterialDesignM.METRONOME)
                toggles({ obj -> obj.quantizationConfig.enableQuantization })
            }
            addAction("Resize object") {
                icon(MaterialDesignA.ARROW_EXPAND_HORIZONTAL)
                executes { obj ->
                    //TODO resize dialog
                }
            }
            addAction("Configure quantization") {
                applicableWhen { obj ->
                    if (!obj.affectsPlayback) reactiveValue(false)
                    else obj.player?.isPlaying?.not() ?: reactiveValue(true)
                }
                icon(Codicons.SYMBOL_PROPERTY)
                executes { obj, ev ->
                    if (obj.quantizationConfig.meter.now.isResolved.now.not()) {
                        val meter = SimpleSearchableRegistryView(obj.context[MeterRegistry], "Select meter")
                            .showPopup(ev) ?: return@executes
                        obj.quantizationConfig.meter.set(meter.reference())
                    }
                    val copy = obj.quantizationConfig.copy()
                    copy.initialize(obj.context)
                    QuantizationConfigDialog(copy, "Configure live loop '${obj.name.now}")
                        .showDialog(ev) ?: return@executes
                    obj.quantizationConfig.update(copy)
                    val newDuration = obj.quantizationConfig.computeDuration()
                    val direction = Direction.horizontal(HorizontalDirection.RIGHT)
                    obj.resize(newDuration, obj.height, ScoreObject.ResizeMode.Regular, direction)
                }
            }
        }
    }
}