package ponticello.sc.editor

import javafx.scene.input.DataFormat
import ponticello.model.obj.InstrumentObject
import ponticello.model.registry.InstrumentRegistry
import ponticello.model.registry.ObjectRegistry
import ponticello.ui.dock.AppLayout
import ponticello.ui.registry.InstrumentRegistryPane

class InstrumentSelector : ObjectSelector<InstrumentObject>() {
    override fun getList(): ObjectRegistry<InstrumentObject> = context[InstrumentRegistry]

    override fun createNewObject(name: String): InstrumentObject? =
        context[AppLayout].get<InstrumentRegistryPane>().createNewObject(name, null)

    override fun dataFormat(): DataFormat = InstrumentObject.DATA_FORMAT
}