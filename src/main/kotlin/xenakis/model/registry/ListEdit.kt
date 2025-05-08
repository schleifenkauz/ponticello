package xenakis.model.registry

import fxutils.undo.AbstractEdit
import xenakis.model.obj.ContextualObject

sealed class ListEdit<O : ContextualObject>(protected val registry: ObjectList<O>) : AbstractEdit() {
    class AddObject<O : ContextualObject>(
        registry: ObjectList<O>,
        private val obj: O,
        private val idx: Int
    ) : ListEdit<O>(registry) {
        override val actionDescription: String
            get() = "Add ${registry.objectType}"

        override fun doUndo() {
            registry.remove(obj)
        }

        override fun doRedo() {
            registry.add(obj, idx)
        }
    }

    class RemoveObject<O : ContextualObject>(
        registry: ObjectList<O>,
        private val obj: O,
        private val idx: Int
    ) : ListEdit<O>(registry) {
        override val actionDescription: String
            get() = "Remove ${registry.objectType}"

        override fun doUndo() {
            registry.add(obj, idx)
        }

        override fun doRedo() {
            registry.remove(obj)
        }
    }

    class MoveObject<O : ContextualObject>(
        registry: ObjectList<O>,
        private val obj: O,
        private val fromIdx: Int,
        private val toIdx: Int
    ) : ListEdit<O>(registry) {
        override val actionDescription: String
            get() = "Move ${registry.objectType}"

        override fun doRedo() {
            registry.move(obj, toIdx)
        }

        override fun doUndo() {
            registry.move(obj, fromIdx)
        }
    }
}
