package ponticello.model.code

import hextant.context.Context
import ponticello.model.registry.ObjectRegistry
import ponticello.sc.client.SuperColliderClient
import reaktive.value.now

class ScriptRegistry(override val objects: MutableList<ScriptObject>) : ObjectRegistry<ScriptObject>() {
    override val objectType: String
        get() = "Script"

    override fun initialize(context: Context) {
        super.initialize(context)
        val client = context[SuperColliderClient]
        client.onServerBooted {
            for (script in objects) {
                if (script.type.now == ScriptObject.Type.AFTER_BOOT) {
                    script.executeContents(client)
                }
            }
        }
        client.onTreeCleared {
            for (script in objects) {
                if (script.type.now == ScriptObject.Type.SERVER_TREE) {
                    script.executeContents(client)
                }
            }
        }
    }

    companion object {
        fun createDefault(): ScriptRegistry = ScriptRegistry(
            mutableListOf(
                ScriptObject.create(ScriptObject.Type.BEFORE_BOOT, "before_boot"),
                ScriptObject.create(ScriptObject.Type.AFTER_BOOT, "after_boot"),
                ScriptObject.create(ScriptObject.Type.SERVER_TREE, "server_tree"),
                ScriptObject.create(ScriptObject.Type.REGULAR, "playground")
            )
        )
    }
}