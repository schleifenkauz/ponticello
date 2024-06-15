package xenakis.sc.editor

import hextant.context.Context
import hextant.serial.Snapshot
import xenakis.model.InstrumentObject
import xenakis.model.InstrumentRegistry
import xenakis.model.ObjectReference
import xenakis.model.ObjectRegistry
import xenakis.ui.InstrumentRegistryPane

class InstrumentSelector(
    context: Context,
    initialValue: InstrumentObject.Reference
) : ObjectSelector<InstrumentObject, InstrumentObject.Reference>(context, initialValue) {
    override val isNullable: Boolean
        get() = false

    override val registry: ObjectRegistry<InstrumentObject>
        get() = context[InstrumentRegistry]

    override fun createNewObject(name: String): InstrumentObject = context[InstrumentRegistryPane].createSynthDef(name)

    override fun createSnapshot(): Snapshot<*> = Snap()

    private class Snap : ObjectSelector.Snap<InstrumentObject, InstrumentObject.Reference>() {
        override val serializer: ObjectReference.Serializer<InstrumentObject.Reference>
            get() = InstrumentObject.Reference.Serializer
    }
}