package xenakis.sc.editor

import hextant.context.Context
import reaktive.value.ReactiveVariable
import xenakis.model.obj.ProcessDefObject
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ObjectRegistry
import xenakis.model.registry.ProcessDefRegistry
import kotlin.reflect.KClass

class ProcessDefSelector(
    context: Context,
    selected: ReactiveVariable<ObjectReference>
) : ObjectSelector<ProcessDefObject, ObjectReference>(context, selected) {
    override fun getRegistry(context: Context): ObjectRegistry<*> = context[ProcessDefRegistry]

    override val objectClass: KClass<ProcessDefObject>
        get() = ProcessDefObject::class

    override fun createNewObject(name: String): ProcessDefObject = ProcessDefObject.newEmpty(name, context)
}