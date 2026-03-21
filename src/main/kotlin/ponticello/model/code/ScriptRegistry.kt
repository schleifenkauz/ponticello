package ponticello.model.code

import com.illposed.osc.OSCMessage
import hextant.context.Context
import ponticello.impl.Logger
import ponticello.model.registry.IdProvider
import ponticello.model.registry.ObjectRegistry
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.getArgument
import reaktive.value.now

class ScriptRegistry(override val objects: MutableList<ScriptObject>) : ObjectRegistry<ScriptObject>() {
    val ids = IdProvider(this)

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
        client.addListener("/run_script") { _, msg ->
            runScript(msg, client)
        }
    }

    private fun runScript(msg: OSCMessage, client: SuperColliderClient) {
        val id = msg.getArgument<Int>(0, "Script ID") ?: return
        val script = ids.getById(id)
        if (script == null) {
            Logger.warn("Received /run_script for unknown script ID: $id", Logger.Category.OSC)
            return
        }
        script.executeContents(client)
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