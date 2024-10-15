package xenakis.model.registry

import hextant.context.Context
import xenakis.model.obj.SuperColliderObject
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient

abstract class SuperColliderObjectRegistry<O : SuperColliderObject> : ObjectRegistry<O>() {
    override fun initialize(context: Context) {
        super.initialize(context)
        context[SuperColliderClient].run {
            for (obj in objects) {
                obj.run { addToServer() }
            }
        }
    }

    open fun syncAll() {
        context[SuperColliderClient].run {
            syncAll(this)
        }
    }

    fun syncAll(writer: ScWriter) {
        for (obj in objects) {
            obj.sync(writer)
        }
    }

    fun ScWriter.allocateAll() {
        for (obj in objects) {
            obj.run { allocateServerObject() }
        }
    }
}