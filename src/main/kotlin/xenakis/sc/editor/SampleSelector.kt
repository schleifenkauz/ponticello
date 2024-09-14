package xenakis.sc.editor

import hextant.context.Context
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.model.ObjectReference
import xenakis.model.ObjectRegistry
import xenakis.model.SampleObject
import xenakis.model.SampleRegistry
import xenakis.ui.SampleRegistryPane
import kotlin.reflect.KClass

class SampleSelector(
    context: Context,
    selected: ReactiveVariable<ObjectReference?> = reactiveVariable(null),
) : ObjectSelector<SampleObject, ObjectReference?>(context, selected) {
    override fun getRegistry(context: Context): ObjectRegistry<SampleObject> = context[SampleRegistry]

    override val objectClass: KClass<SampleObject>
        get() = SampleObject::class

    override fun createNewObject(name: String): SampleObject? = context[SampleRegistryPane].addObject(name)
}