package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.AbstractEditor
import hextant.serial.Snapshot
import hextant.undo.AbstractEdit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import reaktive.value.*
import xenakis.impl.getString
import xenakis.model.NamedObject
import xenakis.model.ObjectReference
import xenakis.model.ObjectRegistry
import xenakis.sc.view.ObjectSelectorView

abstract class ObjectSelector<O : NamedObject, R : ObjectReference<O>>(
    context: Context, initialValue: R
) : AbstractEditor<R, ObjectSelectorView<O>>(context) {
    private var selected = reactiveVariable(initialValue)

    init {
        initialValue.resolve(context)
    }

    override val result: ReactiveValue<R>
        get() = selected

    fun select(value: R) {
        selected.set(value)
        notifyViews { selected(value.get()) }
    }

    abstract val registry: ObjectRegistry<O>

    abstract fun createNewObject(name: String): O

    open fun canSelect(choice: O): ReactiveBoolean = reactiveValue(true)

    open fun extractText(choice: O): ReactiveString = choice.name

    abstract override fun createSnapshot(): Snapshot<*>

    private class Edit<O : NamedObject, R : ObjectReference<O>>(
        private val selector: ObjectSelector<O, R>,
        private val old: R,
        private val new: R
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Select ${selector.registry.objectType}"

        override fun doUndo() {
            selector.select(old)
        }

        override fun doRedo() {
            selector.select(new)
        }
    }

    protected abstract class Snap<O : NamedObject, R : ObjectReference<O>> : Snapshot<ObjectSelector<O, R>>() {
        private lateinit var selected: R

        protected abstract val serializer: ObjectReference.Serializer<R>

        override fun doRecord(original: ObjectSelector<O, R>) {
            selected = original.selected.now
        }

        override fun reconstructObject(original: ObjectSelector<O, R>) {
            original.selected.now = selected
            selected.resolve(original.context)
        }

        override fun encode(builder: JsonObjectBuilder) {
            builder.put("selected", selected.get().name.now)
        }

        override fun decode(element: JsonObject) {
            val name = element.getString("selected")!!
            selected = serializer.createReference(name)
        }
    }
}