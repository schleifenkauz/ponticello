package ponticello.model.registry

import fxutils.undo.AbstractEdit
import ponticello.ui.registry.ListDisplayConfig

sealed class ListEdit<O>(protected val registry: ObjectList<O>) : AbstractEdit() {
    class AddObject<O>(
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

    class RemoveObject<O>(
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

    class MoveObject<O>(
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

    class DropObject<O: Any>(
        private val target: ObjectList<O>,
        private val source: ObjectList<O>,
        private val sourceIdx: Int,
        private val targetIdx: Int,
        private val config: ListDisplayConfig<O>,
        private val obj: O,
    ) : ListEdit<O>(target) {
        override val actionDescription: String
            get() = "Move ${registry.objectType}"

        override fun doRedo() {
            config.dropObject(obj, targetIdx, target, source)
        }

        override fun doUndo() {
            config.dropObject(obj, sourceIdx, source, target)
        }
    }
}
