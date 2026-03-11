package ponticello.ui.flow

import fxutils.alwaysVGrow
import fxutils.drag.setupDropArea
import fxutils.widthAtLeast
import javafx.scene.control.Label
import javafx.scene.input.TransferMode
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.layout.VBox
import ponticello.model.flow.LevelMeterFlow
import ponticello.model.instr.BusObject
import ponticello.model.server.BusRegistry
import ponticello.sc.Rate
import ponticello.sc.editor.BusSelector
import ponticello.sc.view.ObjectSelectorControl
import reaktive.Observer
import reaktive.value.now

class LevelMeterFlowView(private val flow: LevelMeterFlow) : VBox() {
    private val targetObserver: Observer

    init {
        val busSelector = BusSelector()
        busSelector.setFilter(Rate.Audio, null)
        busSelector.syncWith(flow.targetRef)
        busSelector.initialize(flow.context)
        children.add(ObjectSelectorControl(busSelector).widthAtLeast(70.0))
        val initialBus = flow.targetRef.now.get()
        if (initialBus is BusObject.AudioBus) {
            children.add(createLevelMeterCanvas(initialBus))
        } else children.add(Label("Select bus"))
        targetObserver = flow.targetRef.observe { _, _, ref ->
            val bus = ref.get()
            children[1] = if (bus is BusObject.AudioBus) {
                createLevelMeterCanvas(bus)
            } else Label("Select bus")
        }
        setupDropArea {
            handleTypedFormat(BusObject.DATA_FORMAT, TransferMode.LINK) { _, ref ->
                ref.resolve(flow.context[BusRegistry])
                flow.targetRef.set(ref)
                true
            }
        }
        alwaysVGrow()
    }

    private fun createLevelMeterCanvas(initialBus: BusObject.AudioBus): Region {
        val meter = LevelMeter(initialBus, flow.replyId, meterWidth = 12.0)
        val pane = Pane(meter)
        meter.heightProperty().bind(pane.heightProperty())
        return pane.alwaysVGrow()
    }
}