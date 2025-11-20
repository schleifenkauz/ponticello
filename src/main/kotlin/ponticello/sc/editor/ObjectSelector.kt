package ponticello.sc.editor

import hextant.core.editor.SimpleChoiceEditor
import hextant.serial.string
import javafx.scene.input.DataFormat
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import ponticello.model.obj.NamedObject
import ponticello.model.registry.NamedObjectList
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import reaktive.value.*
import reaktive.value.binding.flatMap
import reaktive.value.binding.map

abstract class ObjectSelector<O : NamedObject>() :
    SimpleChoiceEditor<ObjectReference<O>>(), ScExprEditor<ObjectReference<O>> {
    lateinit var isResolved: ReactiveBoolean
        private set

    private var excluded: () -> Collection<O> = { emptyList() }

    abstract fun getList(): NamedObjectList<O>

    open fun filter(obj: O): Boolean = true

    fun exclude(excluded: () -> Collection<O>) {
        this.excluded = excluded
    }

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
        (getList().all() - excluded().toSet()).filter(::filter).map { obj -> obj.reference() }

    abstract fun createNewObject(name: String): O?

    override fun toString(choice: ObjectReference<O>): ReactiveString = choice.isResolved.flatMap { resolved ->
        when {
            choice.getName() == ObjectReference.NONE -> reactiveValue("<none>")
            !resolved -> choice.name.map { n -> "unresolved: $n" }
            else -> toString(choice.get()!!)
        }
    }

    open fun toString(choice: NamedObject): ReactiveValue<String> = choice.name

    open fun dataFormat(): DataFormat? = null

    override fun fromJson(value: JsonElement): ObjectReference<O> = ObjectReference(value.string)

    override fun toJson(value: ObjectReference<O>): JsonElement = JsonPrimitive(value.getName())
}