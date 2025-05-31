package ponticello.ui.registry

import fxutils.controls.SliderBar
import fxutils.setFixedWidth
import fxutils.undo.UndoManager
import javafx.event.Event
import javafx.scene.Node
import ponticello.model.player.ClockObject
import ponticello.model.registry.ClockRegistry
import reaktive.value.reactiveValue

class ClockRegistryPane(clocks: ClockRegistry) : ObjectRegistryPane<ClockObject>(clocks) {
    init {
        setup()
    }

    override fun getItemContent(obj: ClockObject): List<Node> {
        val converter = ClockObject.TIME_WARP_SPEC.converter()
        val name = reactiveValue("Warp")
        val slider = SliderBar(
            obj.timeWarp, name, converter, SliderBar.Style.Regular,
            undoManager = registry.context[UndoManager], updateActionDescription = "Update time warp"
        ).setFixedWidth(150.0)
        return listOf(slider)
    }

    override fun createNewObject(name: String, ev: Event?): ClockObject = ClockObject.withName(name)
}