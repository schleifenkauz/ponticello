package xenakis.model.registry

import bundles.set
import hextant.context.Context
import hextant.context.extend
import hextant.undo.UndoManager
import xenakis.model.obj.SuperColliderObject
import xenakis.sc.client.SuperColliderClient

abstract class SuperColliderObjectRegistry<O : SuperColliderObject> : ObjectRegistry<O>() {
    @Transient
    lateinit var client: SuperColliderClient
        private set

    protected abstract val liveCycleType: SuperColliderObject.LiveCycleType

    override fun initialize(context: Context) {
        val myContext = context.extend {
            set(UndoManager, UndoManager.newInstance())
        }
        super.initialize(myContext)
        client = myContext[SuperColliderClient]
        when (liveCycleType) {
            SuperColliderObject.LiveCycleType.InterpreterBoot -> createAll()
            SuperColliderObject.LiveCycleType.ServerBoot -> client.onServerBooted { createAll() }
            SuperColliderObject.LiveCycleType.ServerTree -> client.onTreeCleared { createAll() }
        }
    }

    private fun createAll() {
        for (obj in all()) {
            client.run {
                obj.run { createObject() }
            }
        }
    }

    override fun syncAll() {
        for (obj in all()) {
            client.run {
                obj.run { sync() }
            }
        }
    }
}