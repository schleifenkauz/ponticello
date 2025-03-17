package xenakis.sc.editor

import xenakis.model.obj.BufferObject
import xenakis.model.registry.BufferRegistry
import xenakis.model.registry.ObjectRegistry

class BufferSelector : ObjectSelector<BufferObject>() {
    override fun getRegistry(): ObjectRegistry<BufferObject> = context[BufferRegistry]

    override fun createNewObject(name: String): BufferObject? {
        throw UnsupportedOperationException("BufferSelector doesn't support creating new object")
    }
}