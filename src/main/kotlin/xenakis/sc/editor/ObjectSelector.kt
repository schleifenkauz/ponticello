package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.AbstractEditor
import hextant.serial.Snapshot
import hextant.undo.AbstractEdit
import hextant.undo.UndoManager
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import reaktive.value.*
import xenakis.impl.getString
import xenakis.model.NamedObject
import xenakis.model.ObjectReference
import xenakis.model.ObjectRegistry
import xenakis.sc.view.ObjectSelectorView
import kotlin.reflect.KClass

abstract class ObjectSelector<O : NamedObject, R : ObjectReference?>(
    context: Context,
    private val selected: ReactiveVariable<R>
) : AbstractEditor<R, ObjectSelectorView<O>>(context) {
    abstract fun getRegistry(context: Context): ObjectRegistry<O>

    abstract val objectClass: KClass<O>

    override val result: ReactiveValue<R>
        get() = selected

    init {
        selected.now?.resolve(this.getRegistry(context))
    }

    fun select(value: R) {
        context[UndoManager].record(Edit(this, selected.now, value))
        selected.set(value)
        notifyViews { selected(value?.get(objectClass)) }
    }

    fun selectInitial(value: R) {
        if (value == null) return
        value.resolve(getRegistry(context))
        selected.set(value)
    }

    override fun viewAdded(view: ObjectSelectorView<O>) {
        view.selected(selected.now?.get(objectClass))
    }

    abstract fun createNewObject(name: String): O?

    open fun canSelect(choice: O): ReactiveBoolean = reactiveValue(true)

    open fun extractText(choice: O): ReactiveString = choice.name

    override fun createSnapshot(): Snapshot<*> = Snap<O, R>()

    private class Edit<O : NamedObject, R : ObjectReference?>(
        private val selector: ObjectSelector<O, R>,
        private val old: R,
        private val new: R
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Select ${selector.getRegistry(selector.context).objectType}"

        override fun doUndo() {
            selector.select(old)
        }

        override fun doRedo() {
            selector.select(new)
        }
    }

    protected class Snap<O : NamedObject, R : ObjectReference?> : Snapshot<ObjectSelector<O, R>>() {
        private var selected: ObjectReference? = null
        private var initialized = false

        override fun doRecord(original: ObjectSelector<O, R>) {
            initialized = true
            selected = original.selected.now
        }

        @Suppress("UNCHECKED_CAST")
        override fun reconstructObject(original: ObjectSelector<O, R>) {
            check(initialized)
            original.selected.now = selected as R
            selected?.resolve(original.getRegistry(original.context))
        }

        override fun encode(builder: JsonObjectBuilder) {
            check(initialized)
            builder.put("selected", selected?.get<NamedObject>()?.name?.now)
        }

        override fun decode(element: JsonObject) {
            val name = element.getString("selected")
            selected = name?.let(::ObjectReference)
            initialized = true
        }
    }
}