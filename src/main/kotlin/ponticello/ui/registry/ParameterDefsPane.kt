package ponticello.ui.registry

import fxutils.prompt.SimpleSearchableListView
import javafx.event.Event
import javafx.scene.input.DragEvent
import ponticello.model.GlobalSettings
import ponticello.model.obj.ParameterDefObject
import ponticello.model.project.PonticelloProject
import ponticello.model.registry.ObjectList
import ponticello.sc.ParameterType
import ponticello.sc.defaultControlSpec
import ponticello.ui.dock.SearchableToolPane
import ponticello.ui.dock.Side
import ponticello.ui.dock.ToolPane

class ParameterDefsPane(
    parameters: ParameterDefList, override val title: String,
) : SearchableToolPane<ParameterDefObject>(parameters), ListDisplayConfig<ParameterDefObject> by ParameterListConfig() {
    override val type: Type
        get() = ParameterDefsPane

    init {
        setup()
    }

    override fun filter(obj: ParameterDefObject): Boolean = super<SearchableToolPane>.filter(obj)

    override fun createNewObject(name: String, ev: Event?): ParameterDefObject? {
        val type = SimpleSearchableListView(ParameterType.regularTypes, "Parameter type")
            .showPopup(listView, initialOption = ParameterType.Numerical) ?: return null
        val spec = type.defaultControlSpec()
        return ParameterDefObject(name, spec)
    }

    override fun createNewObject(ev: Event?, list: ObjectList<ParameterDefObject>): ParameterDefObject? =
        super<SearchableToolPane>.createNewObject(ev, list)

    override fun getDroppedObject(ev: DragEvent, targetView: ObjectListView<ParameterDefObject>): ParameterDefObject? {
        return super<ListDisplayConfig>.getDroppedObject(ev, targetView)
    }

    companion object : Type(-1, "Parameters") {
        override val defaultSide: Side
            get() = Side.RIGHT

        override fun createToolPane(project: PonticelloProject): ToolPane =
            ParameterDefsPane(project.context[GlobalSettings].defaultParametersDefs, "Parameter definitions")
    }
}