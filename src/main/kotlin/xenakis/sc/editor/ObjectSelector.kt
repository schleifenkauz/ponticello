package xenakis.sc.editor

import hextant.core.editor.SimpleChoiceEditor
import reaktive.value.*
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import xenakis.model.registry.NamedObject
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ObjectRegistry
import xenakis.model.registry.reference

abstract class ObjectSelector<O : NamedObject> :
    SimpleChoiceEditor<ObjectReference<O>>(), ScExprEditor<ObjectReference<O>> {
    lateinit var isResolved: ReactiveBoolean
        private set

    abstract fun getRegistry(): ObjectRegistry<O>

    protected open fun filter(obj: O): Boolean = true

    fun selectInitial(value: O) {
        selectInitial(value.reference())
    }

    override fun doInitialize() {
        super.doInitialize()
        result.now.resolve(getRegistry())
        isResolved = result.flatMap { ref -> ref.isResolved }
    }

    override fun choices(): List<ObjectReference<O>> =
        getRegistry().all().filter(::filter).map { obj -> obj.reference() }

    abstract fun createNewObject(name: String): O?

    override fun toString(choice: ObjectReference<O>): ReactiveString = choice.isResolved.flatMap { resolved ->
        when {
            choice.getName() == ObjectReference.NONE -> reactiveValue("(none)")
            !resolved -> choice.name.map { n -> "unresolved: $n" }
            else -> toString(choice.get()!!)
        }
    }

    open fun toString(choice: NamedObject): ReactiveValue<String> = choice.name
}