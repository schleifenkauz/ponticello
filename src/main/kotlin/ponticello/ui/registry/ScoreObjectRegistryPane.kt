package ponticello.ui.registry

import fxutils.actions.ContextualizedAction
import fxutils.actions.action
import fxutils.actions.collectActions
import javafx.event.Event
import javafx.scene.Parent
import javafx.scene.input.DataFormat
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.codicons.Codicons
import org.kordamp.ikonli.materialdesign2.MaterialDesignA
import org.kordamp.ikonli.materialdesign2.MaterialDesignM
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import ponticello.model.project.PonticelloProject
import ponticello.model.project.objects
import ponticello.model.registry.MeterRegistry
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ScoreObject
import ponticello.ui.actions.undoable
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import ponticello.ui.live.QuantizationConfigDialog
import ponticello.ui.live.ScoreObjectResizeDialog
import ponticello.ui.registry.ObjectListView.DisplayMode
import ponticello.ui.score.ScoreObjectPlayerPane
import reaktive.value.binding.not
import reaktive.value.now
import reaktive.value.reactiveValue

class ScoreObjectRegistryPane(registry: ScoreObjectRegistry) : ObjectRegistryPane<ScoreObject>(registry) {
    override val type: Type
        get() = ScoreObjectRegistryPane

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.SubWindow)

    override fun defaultState(): ToolPaneState = ToolPaneState.docked

    override fun getContent(obj: ScoreObject, mode: DisplayMode): Parent = ScoreObjectPlayerPane.getPane(obj)

    override fun getActions(box: ObjectBox<ScoreObject>): List<ContextualizedAction> {
        return actions.withContext(box.obj)
    }

    override val dataFormat: DataFormat
        get() = ScoreObject.DATA_FORMAT

    override fun createNewObject(name: String, ev: Event?): ScoreObject? = null

    companion object : Type(4, "Score Objects") {

        override val icon: Ikon
            get() = MaterialDesignP.PLAYLIST_PLAY

        override val defaultSide: Side
            get() = Side.LEFT

        override fun createToolPane(project: PonticelloProject): ToolPane = ScoreObjectRegistryPane(project.objects)

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
                obj.resize(newDuration, obj.height, ScoreObject.ResizeMode.Regular, javafx.geometry.Side.RIGHT)
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