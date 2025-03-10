package xenakis.ui.flow

import fxutils.actions.button
import fxutils.prompt.SimpleSearchableListView
import fxutils.setupDropArea
import fxutils.styleClass
import javafx.scene.control.ScrollPane
import javafx.scene.control.ScrollPane.ScrollBarPolicy
import javafx.scene.input.DragEvent
import javafx.scene.input.TransferMode
import javafx.scene.layout.BorderPane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import xenakis.model.flow.*
import xenakis.model.obj.BusObject
import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.registry.ObjectReference
import xenakis.ui.impl.getFrom
import xenakis.ui.launcher.XenakisLauncher

class VerticalFlowsBox(
    private val flows: AudioFlows,
    private val associatedBus: BusObject
) : VBox(5.0) {
    val vbox = VBox(5.0)
    private val busBox = BusObjectBox(associatedBus)

    private val flowBoxes = mutableMapOf<AudioFlow, FlowBox<*>>()

    init {
        val addFlowRegion = makeAddFlowButton()
        val scrollPane = ScrollPane(vbox)
        scrollPane.isFitToWidth = true
        scrollPane.hbarPolicy = ScrollBarPolicy.NEVER
        children.addAll(scrollPane, addFlowRegion, busBox)
        setVgrow(addFlowRegion, Priority.ALWAYS)
        setupDropArea({ db -> db.hasContent(AudioFlow.DATA_FORMAT) }, ::onDrop)
    }

    private fun onDrop(ev: DragEvent) {
        val reference = ev.dragboard.getContent(AudioFlow.DATA_FORMAT) as AudioFlows.FlowReference
        val flow = reference.getFrom(flows)
        var newIndex = vbox.children.binarySearchBy(ev.y) { n -> n.layoutY }
        if (newIndex < 0) newIndex = -(newIndex + 1)
        if (flow.associatedBus != associatedBus) {
            val copy = flow.copy()
            copy.initialize(flows.context, associatedBus)
            flows.addFlow(copy, newIndex)
            if (TransferMode.COPY !in ev.dragboard.transferModes) {
                flows.removeFlow(flow)
            }
        } else {
            if (TransferMode.MOVE !in ev.dragboard.transferModes) return
            flow.context[XenakisLauncher.currentProject].flows.moveFlow(flow, newIndex)
        }
    }

    private fun makeAddFlowButton(): BorderPane {
        val btn = MaterialDesignP.PLUS.button("Add flow").styleClass("large-icon-button")
        val pane = BorderPane(btn)
        pane.setupDropArea({ db -> db.hasContent(SynthDefObject.DATA_FORMAT) }) { ev ->
            val registry = flows.context[InstrumentRegistry]
            val def = ObjectReference(ev.dragboard.getFrom(registry, SynthDefObject.DATA_FORMAT) as SynthDefObject)
            val flow = SynthFlow.createFor(associatedBus, def.get(), flows.context)
            flows.addFlow(flow)
        }
        btn.setOnMouseClicked {
            val options = FlowOption.getOptions(flows.context)
            SimpleSearchableListView(options, "Add flow").showPopup(anchorNode = btn) { option ->
                option.createFlow(flows.context, btn, associatedBus) { flow ->
                    flow.initialize(flows.context, associatedBus)
                    flows.addFlow(flow)
                }
            }
        }
        return pane
    }

    fun addedFlow(flow: AudioFlow, index: Int) {
        val box = when (flow) {
            is CodeFlow -> CodeFlowBox(flow)
            is SynthFlow -> SynthFlowBox(flow)
            is UtilityFlow -> UtilityFlowBox(flow)
            is SendFlow -> SendUtilityBox(flow)
            is ScoreObjectPlaceholder -> PlaceholderBox(flow)
        }
        box.initialize()
        flowBoxes[flow] = box
        vbox.children.add(index, box)
    }

    fun removedFlow(flow: AudioFlow) {
        val box = flowBoxes.remove(flow)
        vbox.children.remove(box)
    }

    fun movedFlow(oldIndex: Int, newIndex: Int) {
        val box = vbox.children.removeAt(oldIndex)
        vbox.children.add(newIndex, box)
    }
}