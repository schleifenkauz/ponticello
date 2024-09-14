package xenakis.sc.editor

import hextant.context.Context
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.model.InstrumentObject
import xenakis.model.InstrumentRegistry
import xenakis.model.ObjectReference
import xenakis.model.ObjectRegistry
import xenakis.ui.InstrumentRegistryPane
import kotlin.reflect.KClass

class InstrumentSelector(
    context: Context,
    selected: ReactiveVariable<ObjectReference>,
) : ObjectSelector<InstrumentObject, ObjectReference>(context, selected) {
    constructor(context: Context) : this(
        context, reactiveVariable(context[InstrumentRegistry].getDefault().createReference())
    )

    override fun getRegistry(context: Context): ObjectRegistry<InstrumentObject> = context[InstrumentRegistry]

    override val objectClass: KClass<InstrumentObject>
        get() = InstrumentObject::class

    override fun createNewObject(name: String): InstrumentObject? = context[InstrumentRegistryPane].createSynthDef(name)
}