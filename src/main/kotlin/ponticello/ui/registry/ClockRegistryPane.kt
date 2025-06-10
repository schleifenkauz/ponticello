package ponticello.ui.registry

import fxutils.controls.SliderBar
import fxutils.setFixedWidth
import fxutils.undo.UndoManager
import javafx.event.Event
import javafx.scene.Node
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.material2.Material2AL
import ponticello.model.player.ClockObject
import ponticello.model.project.CLOCKS
import ponticello.model.project.PonticelloProject
import ponticello.model.project.get
import ponticello.model.registry.ClockRegistry
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane
import ponticello.ui.dock.ToolPaneState
import reaktive.value.reactiveValue

class ClockRegistryPane(clocks: ClockRegistry) : ObjectRegistryPane<ClockObject>(clocks) {
    override val type: Type
        get() = ClockRegistryPane

    override fun defaultState(): ToolPaneState = ToolPaneState.docked

    override fun getItemContent(obj: ClockObject): List<Node> {
        val converter = ClockObject.TIME_WARP_SPEC.converter()
        val name = reactiveValue("Time Warp")
        val slider = SliderBar(
            obj.timeWarp, name, converter, SliderBar.Style.Regular,
            undoManager = registry.context[UndoManager], updateActionDescription = "Update time warp"
        ).setFixedWidth(150.0)
        return listOf(slider)
    }

    override fun createNewObject(name: String, ev: Event?): ClockObject = ClockObject.withName(name)

    companion object : Type {
        override val uid: Int
            get() = 2

        override val title: String
            get() = "Clocks"

        override val icon: Ikon
            get() = Material2AL.ACCESS_TIME

        override val defaultSide: Side
            get() = Side.LEFT

        override fun createToolPane(project: PonticelloProject): ToolPane = ClockRegistryPane(project[CLOCKS])
    }
}