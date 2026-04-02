package ponticello.sc.editor

import fxutils.prompt.PromptPlacement
import javafx.scene.input.DataFormat
import ponticello.model.instr.InstrumentObject
import ponticello.model.instr.InstrumentRegistry
import ponticello.ui.dock.AppLayout
import ponticello.ui.registry.InstrumentRegistryPane

class InstrumentSelector : ObjectSelector<InstrumentObject>() {
    override fun getOptions(): List<InstrumentObject> = context[InstrumentRegistry]

    override fun createNewObject(name: String, promptPlacement: PromptPlacement): InstrumentObject? =
        context[AppLayout].get<InstrumentRegistryPane>().createNewObject(name, null)

    override fun dataFormat(): DataFormat = InstrumentObject.DATA_FORMAT

    override val canViewSelected: Boolean
        get() = true

    override fun viewObject(obj: InstrumentObject) {
        context[AppLayout].get<InstrumentRegistryPane>().showContent(obj)
    }
}