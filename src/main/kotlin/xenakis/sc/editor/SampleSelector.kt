package xenakis.sc.editor

import hextant.context.Context
import reaktive.value.ReactiveVariable
import reaktive.value.reactiveVariable
import xenakis.model.obj.SampleObject
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ObjectRegistry
import xenakis.model.registry.SampleRegistry
import xenakis.ui.registry.SampleRegistryPane
import kotlin.reflect.KClass

class SampleSelector(
    context: Context,
    selected: ReactiveVariable<ObjectReference?> = reactiveVariable(null),
) : ObjectSelector<SampleObject, ObjectReference?>(context, selected) {
    override fun getRegistry(context: Context): ObjectRegistry<*> = context[SampleRegistry]

    override val objectClass: KClass<SampleObject>
        get() = SampleObject::class

    override fun createNewObject(name: String): SampleObject? = context[SampleRegistryPane].addObject(name)
}