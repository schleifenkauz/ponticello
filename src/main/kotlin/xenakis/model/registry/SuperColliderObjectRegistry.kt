package xenakis.model.registry

import hextant.context.Context
import reaktive.Observer
import xenakis.model.obj.SuperColliderObject
import xenakis.sc.client.SuperColliderClient

abstract class SuperColliderObjectRegistry<O : SuperColliderObject> : ObjectRegistry<O>() {
    @Transient
    lateinit var client: SuperColliderClient
        private set

    protected abstract val liveCycleType: SuperColliderObject.LiveCycleType
    private var liveCycleObserver: Observer? = null

    override fun initialize(context: Context) {
        super.initialize(context)
        client = context[SuperColliderClient]
        liveCycleObserver = when (liveCycleType) {
            SuperColliderObject.LiveCycleType.InterpreterBoot -> {
                createAll()
                null
            }
            SuperColliderObject.LiveCycleType.ServerBoot -> client.serverRebooted.observe { _ -> createAll() }
            SuperColliderObject.LiveCycleType.ServerTree -> client.treeCleared.observe { _ -> createAll() }
        }
    }

    private fun createAll() {
        client.run {
            for (obj in all()) {
                obj.run { createObject() }
            }
        }
    }

    open fun syncAll() {
        client.run {
            for (obj in all()) {
                obj.run { sync() }
            }
        }
    }
}