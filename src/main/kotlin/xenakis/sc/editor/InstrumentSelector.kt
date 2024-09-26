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

class InstrumentSelector<R: ObjectReference?>(
    context: Context,
    selected: ReactiveVariable<R>,
) : ObjectSelector<InstrumentObject, R>(context, selected) {
    constructor(context: Context) : this(
        context, reactiveVariable<R>(context[InstrumentRegistry].getDefault().createReference() as R)
    )

    override fun getRegistry(context: Context): ObjectRegistry<*> = context[InstrumentRegistry]

    override val objectClass: KClass<InstrumentObject>
        get() = InstrumentObject::class

    override fun createNewObject(name: String): InstrumentObject? = context[InstrumentRegistryPane].createSynthDef(name)
}