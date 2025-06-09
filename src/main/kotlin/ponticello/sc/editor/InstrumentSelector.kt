package ponticello.sc.editor

import javafx.scene.input.DataFormat
import ponticello.model.obj.InstrumentObject
import ponticello.model.registry.InstrumentRegistry
import ponticello.model.registry.ObjectRegistry
import ponticello.ui.launcher.PonticelloMainActivity

class InstrumentSelector : ObjectSelector<InstrumentObject>() {
    override fun getList(): ObjectRegistry<InstrumentObject> = context[InstrumentRegistry]

    override fun createNewObject(name: String): InstrumentObject? =
        context[PonticelloMainActivity].instrumentsPane().createNewObject(name, null)

    override fun dataFormat(): DataFormat = InstrumentObject.DATA_FORMAT
}