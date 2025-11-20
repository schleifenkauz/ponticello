package ponticello.model.registry

import hextant.context.Context
import hextant.context.extend
import ponticello.model.obj.SuperColliderObject
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run

abstract class SuperColliderObjectRegistry<O : SuperColliderObject> : ObjectRegistry<O>() {
    @Transient
    lateinit var client: SuperColliderClient
        private set

    protected abstract val liveCycleType: SuperColliderObject.LiveCycleType

    override fun initialize(context: Context) {
        val myContext = context.extend {
            //set(UndoManager, context[UndoManager].createSubManager())
        }
        super.initialize(myContext)
        client = myContext[SuperColliderClient]
        when (liveCycleType) {
            SuperColliderObject.LiveCycleType.InterpreterBoot -> createAll()
            SuperColliderObject.LiveCycleType.ServerBoot -> client.onServerBooted { createAll() }
            SuperColliderObject.LiveCycleType.ServerTree -> client.onTreeCleared { createAll() }
        }
    }

    override fun onAdded(obj: O, idx: Int) {
        client.run {
            with(obj) { createObject() }
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