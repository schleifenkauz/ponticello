package xenakis.sc.editor

import hextant.core.editor.SimpleChoiceEditor
import hextant.serial.string
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import reaktive.value.*
import reaktive.value.binding.flatMap
import reaktive.value.binding.map
import xenakis.model.registry.NamedObject
import xenakis.model.registry.NamedObjectList
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.reference

abstract class ObjectSelector<O : NamedObject> :
    SimpleChoiceEditor<ObjectReference<O>>(), ScExprEditor<ObjectReference<O>> {
    lateinit var isResolved: ReactiveBoolean
        private set

    abstract fun getList(): NamedObjectList<O>

    open fun filter(obj: O): Boolean = true

    fun selectInitial(value: O) {
        selectInitial(value.reference())
    }

    override fun doInitialize() {
        result.now.resolve(getList())
        isResolved = result.flatMap { ref -> ref.isResolved }
    }

    override fun setupDefaultState() {
        selectInitial(ObjectReference.none())
    }

    override fun choices(): List<ObjectReference<O>> =
        getList().all().filter(::filter).map { obj -> obj.reference() }

    abstract fun createNewObject(name: String): O?

    override fun toString(choice: ObjectReference<O>): ReactiveString = choice.isResolved.flatMap { resolved ->
        when {
            choice.getName() == ObjectReference.NONE -> reactiveValue("<none>")
            !resolved -> choice.name.map { n -> "unresolved: $n" }
            else -> toString(choice.get()!!)
        }
    }

    open fun toString(choice: NamedObject): ReactiveValue<String> = choice.name

    override fun fromJson(value: JsonElement): ObjectReference<O> = ObjectReference<O>(value.string)

    override fun toJson(value: ObjectReference<O>): JsonElement = JsonPrimitive(value.getName())
}