package ponticello.sc.editor

import javafx.scene.input.DataFormat
import ponticello.model.code.OSCHookObject
import ponticello.model.obj.project
import ponticello.model.project.OSC_HOOKS
import ponticello.model.project.get
import ponticello.ui.dock.AppLayout
import ponticello.ui.registry.OSCHookRegistryPane

class OSCHookSelector : ObjectSelector<OSCHookObject>() {
    override val objectType: String
        get() = "OSC Hook"

    override fun getOptions(): List<OSCHookObject> = context.project[OSC_HOOKS]

    override val canViewSelected: Boolean get() = true

    override fun viewObject(obj: OSCHookObject) {
        context[AppLayout].get<OSCHookRegistryPane>().showContent(obj)
    }

    override fun dataFormat(): DataFormat = OSCHookObject.DATA_FORMAT
}