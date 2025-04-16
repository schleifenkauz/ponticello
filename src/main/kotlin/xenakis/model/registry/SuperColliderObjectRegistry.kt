package xenakis.model.registry

import hextant.context.Context
import xenakis.model.obj.SuperColliderObject
import xenakis.sc.client.SuperColliderClient

abstract class SuperColliderObjectRegistry<O : SuperColliderObject> : ObjectRegistry<O>() {
    @Transient
    lateinit var client: SuperColliderClient
        private set

    protected abstract val liveCycleType: SuperColliderObject.LiveCycleType

    override fun initialize(context: Context) {
        super.initialize(context)
        client = context[SuperColliderClient]
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