package ponticello.ui.registry

import fxutils.Direction
import fxutils.SubWindow
import fxutils.actions.ContextualizedAction
import fxutils.actions.action
import fxutils.actions.collectActions
import fxutils.controls.SliderBar
import fxutils.undo.UndoManager
import javafx.event.Event
import javafx.geometry.HorizontalDirection
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.input.DataFormat
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import org.kordamp.ikonli.materialdesign2.MaterialDesignM
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import ponticello.impl.one
import ponticello.impl.toDecimal
import ponticello.impl.zero
import ponticello.model.obj.ParameterizedObject
import ponticello.model.registry.MeterRegistry
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ScoreObject
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Warp
import ponticello.ui.actions.undoable
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.live.QuantizationConfigDialog
import ponticello.ui.live.ScoreObjectResizeDialog
import ponticello.ui.midi.ContextualMidiReceiver
import ponticello.ui.midi.ParameterControlsMidiContext
import ponticello.ui.registry.ObjectListView.DisplayMode
import ponticello.ui.score.ScoreObjectViewPane
import reaktive.value.binding.not
import reaktive.value.now
import reaktive.value.reactiveValue

class ScoreObjectRegistryPane(registry: ScoreObjectRegistry) : ObjectRegistryPane<ScoreObject>(registry) {
    override val title: String
        get() = "Score Objects"

    override val icon: Ikon
        get() = MaterialDesignP.PLAYLIST_PLAY

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.SubWindow)

    override fun defaultState(): ToolPaneState = ToolPaneState.docked(ToolPaneState.Side.LEFT)

    override fun getItemContent(obj: ScoreObject): List<Node> {
        val spec = NumericalControlSpec(zero, zero, one, 0.01.toDecimal(), zero, Warp.Linear)
        val scoreYSlider = SliderBar(
            obj.liveConfig.yPosition, "Score Y", spec.converter(),
            undoManager = obj.context[UndoManager]
        )
        scoreYSlider.prefWidth = 150.0
        return listOf(scoreYSlider)
    }

    override fun configureSubWindow(window: SubWindow, obj: ScoreObject) {
        if (obj is ParameterizedObject) {
            val content = window.scene.root as? ScoreObjectViewPane ?: return
            registry.context[ContextualMidiReceiver].registerMidiContext(window) {
                ParameterControlsMidiContext(obj.controls, content::isShowingDetailsPane)
            }
        }
    }

    override fun getContent(obj: ScoreObject, mode: DisplayMode): Parent = ScoreObjectViewPane.getPane(obj)

    override fun getActions(box: ObjectBox<ScoreObject>): List<ContextualizedAction> {
        return actions.withContext(box.obj)
    }

    override fun dataFormat(obj: ScoreObject): DataFormat = ScoreObject.DATA_FORMAT

    override fun createNewObject(name: String, ev: Event?): ScoreObject? = null

    companion object {
        val configureQuantizationAction = action<ScoreObject>("Configure quantization") {
            enableWhen { obj ->
                if (!obj.affectsPlayback) reactiveValue(false)
                else obj.player?.isScheduled?.not() ?: reactiveValue(true)
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

        val toggleLoopingAction = action<ScoreObject>("Toggle looping") {
            applicableIf { obj -> obj.affectsPlayback }
            toggles(
                { obj -> obj.liveConfig.loop },
                whenFalse = MaterialDesignR.REPEAT_OFF,
                whenTrue = MaterialDesignR.REPEAT,
            )
            undoable()
        }

        val quantizeStartAction = action<ScoreObject>("Quantize start") {
            applicableIf { obj -> obj.affectsPlayback }
            icon(MaterialDesignM.METRONOME)
            undoable()
            toggles({ obj -> obj.quantizationConfig.enableQuantization })
        }

        val resizeObjectAction = action("Resize object") {
            icon(MaterialDesignA.ARROW_EXPAND_HORIZONTAL)
            executes { obj, ev -> ScoreObjectResizeDialog.show(obj, ev) }
        }

        val actions = collectActions {
            add(toggleLoopingAction)
            add(quantizeStartAction)
            add(resizeObjectAction)
            add(configureQuantizationAction)
        }
    }
}