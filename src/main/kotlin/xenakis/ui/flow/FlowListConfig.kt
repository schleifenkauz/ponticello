package xenakis.ui.flow

import bundles.createBundle
import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import fxutils.controls.SliderBar
import fxutils.prompt.InfoPrompt
import fxutils.setFixedWidth
import fxutils.styleClass
import fxutils.undo.UndoManager
import javafx.scene.Node
import javafx.scene.Parent
import javafx.scene.control.Slider
import javafx.scene.input.DataFormat
import javafx.scene.input.DragEvent
import javafx.scene.input.Dragboard
import javafx.scene.input.TransferMode
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import reaktive.value.ReactiveString
import reaktive.value.binding.binding
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.flow.*
import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.SynthDefRegistry
import xenakis.sc.Rate
import xenakis.sc.editor.BusSelector
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.actions.ServerActions
import xenakis.ui.impl.getFrom
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.registry.ObjectBox
import xenakis.ui.registry.ObjectListDisplayConfig
import xenakis.ui.registry.ObjectListView
import xenakis.ui.registry.ObjectListView.DisplayMode
import xenakis.ui.registry.SimpleSearchableRegistryView
import xenakis.ui.score.ParameterControlsPane

class FlowListConfig(
    private val group: AudioFlowGroup,
    private val autoResizeScene: Boolean,
) : ObjectListDisplayConfig<AudioFlow> {
    private val context get() = group.context

    override val buttonStyle: String
        get() = "flow-action-button"
    override val enableReordering: Boolean
        get() = true

    override val supportedModes: Set<DisplayMode>
        get() = setOf(DisplayMode.Inline, DisplayMode.DetailsPane)

    fun canDrop(db: Dragboard): Boolean = when {
        db.hasContent(AudioFlow.DATA_FORMAT) -> true
        db.hasContent(SynthDefObject.DATA_FORMAT) ->
            db.getFrom(context[SynthDefRegistry], SynthDefObject.DATA_FORMAT) != null

        else -> false
    }

    override fun getItemContent(obj: AudioFlow): List<Node> = when (obj) {
        is MixerFlow -> {
            val selector = BusSelector()
            selector.setFilter(rate = Rate.Audio, channels = null)
            selector.syncWith(obj.targetBus)
            selector.initialize(context)
            val masterVolumeSlider = SliderBar(
                obj.masterVolume, reactiveValue("Master volume"),
                MixerFlow.VOLUME_SPEC.converter(unit = "db"),
                SliderBar.Style.AlwaysValue,
                undoManager = obj.context[UndoManager]
            ).setFixedWidth(150.0)
            val selectorControl = ObjectSelectorControl(selector, createBundle())
            listOf(selectorControl, masterVolumeSlider)
        }

        else -> emptyList()
    }

    override fun getContent(obj: AudioFlow, mode: DisplayMode): Parent = when (obj) {
        is CodeFlow -> obj.codeEditor.control
        is SendFlow -> SendFlowView(obj)
        is SynthFlow -> ParameterControlsPane(obj, "Flow controls").apply {
            listView.autoResizeScene = autoResizeScene
        }

        is ProcessFlow -> ParameterControlsPane(obj, "Flow controls").apply {
            listView.autoResizeScene = autoResizeScene
        }

        is UtilityFlow -> Slider(-60.0, +6.0, 0.0) styleClass "volume-fader"
        is MixerFlow -> MixerFlowView(obj).apply {
            componentsView.autoResizeScene = autoResizeScene
        }
    }

    override fun getActions(box: ObjectBox<AudioFlow>): List<ContextualizedAction> {
        return actions.withContext(box.obj)
    }

    override fun dataFormat(obj: AudioFlow): DataFormat = AudioFlow.DATA_FORMAT

    override fun configureDragboard(obj: AudioFlow, dragboard: Dragboard) {
        val ref = AudioFlows.FlowReference(group.name.now, obj.name.now)
        dragboard.setContent(mapOf(AudioFlow.DATA_FORMAT to ref))
    }

    override fun getDefaultDisplayName(obj: AudioFlow): ReactiveString = obj.getDefaultName()

    fun onDrop(ev: DragEvent, listView: ObjectListView<AudioFlow>) {
        if (ev.dragboard.hasContent(AudioFlow.DATA_FORMAT)) {
            val reference = ev.dragboard.getContent(AudioFlow.DATA_FORMAT) as AudioFlows.FlowReference
            val flow = reference.getFlow(context[AudioFlows]) ?: return
            var newIndex = listView.getBoxes().binarySearchBy(ev.y) { n -> n.layoutY }
            if (newIndex < 0) newIndex = -(newIndex + 1)
            val copy = flow.copy()
            listView.source.add(copy, newIndex)
            if (flow.isActive.now) copy.activate()
            if (TransferMode.COPY !in ev.dragboard.transferModes) {
                reference.removeFrom(context[AudioFlows])
            }
        } else if (ev.dragboard.hasContent(SynthDefObject.DATA_FORMAT)) {
            val registry = context[SynthDefRegistry]
            val def = ev.dragboard.getFrom(registry, SynthDefObject.DATA_FORMAT) ?: return
            val flow = SynthFlow.create(def, context)
            listView.source.add(flow)
        }
    }

    companion object {
        private val actions = collectActions<AudioFlow> {
            addAction("Add source bus") {
                icon(MaterialDesignP.PLUS)
                executesOn<MixerFlow> { flow, ev ->
                    val bus = SimpleSearchableRegistryView(flow.context[BusRegistry], "Select source bus")
                        .showPopup(ev) ?: return@executesOn
                    flow.components.add(MixerFlow.MixerComponent.create(bus))
                }
            }
            add(ServerActions.scopeBus) { f -> (f as? MixerFlow)?.targetBus }
            addAction("Toggle activated") {
                icon { flow ->
                    binding(flow.isValid, flow.isActive) { valid, active ->
                        when {
                            !valid -> MaterialDesignC.CLOSE_CIRCLE_OUTLINE
                            active -> MaterialDesignR.RADIOBOX_MARKED
                            else -> MaterialDesignR.RADIOBOX_BLANK
                        }
                    }
                }
                shortcut("Ctrl+T")
                executes { flow, ev ->
                    if (!flow.isValid.now) {
                        InfoPrompt("Cannot activate flow").showDialog(ev) //TODO better error description
                        return@executes
                    }
                    if (flow.isActive.now) flow.deactivate()
                    else flow.activate()
                }
            }
            addAction("Reload") {
                icon(MaterialDesignS.SYNC)
                shortcut("Ctrl+U")
                enableWhen { flow -> flow.isActive }
                executes { flow -> flow.sync() }
            }
            addAction("View SynthDef") {
                icon(Material2AL.CODE)
                shortcut("Ctrl+L")
                enableWhen { flow ->
                    if (flow !is SynthFlow) reactiveValue(false)
                    else flow.synthDefSelector.isResolved
                }
                executes { flow ->
                    flow as SynthFlow
                    val pane = flow.context[XenakisMainActivity].synthDefsPane
                    pane.listView.showContent(flow.def)
                }
            }
        }
    }
}