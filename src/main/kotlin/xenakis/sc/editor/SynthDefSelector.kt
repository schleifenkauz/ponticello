package xenakis.sc.editor

import hextant.context.Context
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.model.obj.SynthDefObject
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ObjectRegistry
import xenakis.ui.launcher.XenakisMainScreen
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

    override fun createNewObject(name: String): SynthDefObject? =
        context[XenakisMainScreen].instrumentsPane.createSynthDef(name)
}