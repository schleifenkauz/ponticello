package xenakis.sc.editor

import hextant.context.Context
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.model.obj.InstrumentObject
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ObjectRegistry
import xenakis.ui.XenakisUI
import kotlin.reflect.KClass

class InstrumentSelector<R : ObjectReference?>(
    context: Context,
    selected: ReactiveVariable<R>,
) : ObjectSelector<InstrumentObject, R>(context, selected) {
    @Suppress("UNCHECKED_CAST")
    constructor(context: Context) : this(
        context, reactiveVariable<R>(context[InstrumentRegistry].getDefault().createReference() as R)
    )

    override fun getRegistry(context: Context): ObjectRegistry<*> = context[InstrumentRegistry]

    override val objectClass: KClass<InstrumentObject>
        get() = InstrumentObject::class

    override fun createNewObject(name: String): InstrumentObject? =
        context[XenakisUI].instrumentsPane.createSynthDef(name)
}