package xenakis.sc.editor

import hextant.context.Context
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.model.InstrumentRegistry
import xenakis.model.ObjectReference
import xenakis.model.ObjectRegistry
import xenakis.model.SynthDefObject
import xenakis.ui.InstrumentRegistryPane
import kotlin.reflect.KClass

class SynthDefSelector(
    context: Context,
    selected: ReactiveVariable<ObjectReference>,
) : ObjectSelector<SynthDefObject, ObjectReference>(context, selected) {
    constructor(context: Context) : this(
        context, reactiveVariable(context[InstrumentRegistry].getDefault().createReference())
    )

    override fun getRegistry(context: Context): ObjectRegistry<*> = context[InstrumentRegistry]

    override val objectClass: KClass<SynthDefObject>
        get() = SynthDefObject::class

    override fun createNewObject(name: String): SynthDefObject? = context[InstrumentRegistryPane].createSynthDef(name)
}