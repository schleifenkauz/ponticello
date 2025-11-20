package ponticello.sc.editor

import javafx.scene.input.DataFormat
import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.InstrumentRegistry
import ponticello.model.registry.ObjectRegistry
import ponticello.ui.dock.AppLayout
import ponticello.ui.registry.InstrumentRegistryPane

class InstrumentSelector : ObjectSelector<InstrumentObject>() {
    override fun getList(): ObjectRegistry<InstrumentObject> = context[InstrumentRegistry]

    override fun createNewObject(name: String): InstrumentObject? =
        context[AppLayout].get<InstrumentRegistryPane>().createNewObject(name, null)

    override fun dataFormat(): DataFormat = InstrumentObject.DATA_FORMAT
}