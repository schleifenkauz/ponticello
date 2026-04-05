package ponticello.model.code

import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import hextant.context.Context
import ponticello.impl.Logger
import ponticello.model.obj.SuperColliderObject
import ponticello.model.registry.SuperColliderObjectRegistry
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.getArgument

class OSCHookRegistry(
    override val objects: MutableList<OSCHookObject>
) : SuperColliderObjectRegistry<OSCHookObject>(), OSCMessageListener {
    override val objectType: String
        get() = "OSC Hook"

    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.InterpreterBoot

    override fun initialize(context: Context) {
        super.initialize(context)
        context[SuperColliderClient].addListener(this)
    }

    override fun acceptMessage(ev: OSCMessageEvent) {
        if (!ev.message.address.startsWith("/osc_hook")) return
        val hookName = ev.message.getArgument<String>(0, "hook_name") ?: return
        val obj = getOrNull(hookName)
        if (obj == null) {
            Logger.warn("Received OSC hook message: Hook '$hookName' not found.", Logger.Category.OSC)
            return
        }
        when (ev.message.address) {
            "/osc_hook" -> obj.addEvent(ev)
            "/osc_hook_disabled" -> obj.updateEnabled(false)
            "/osc_hook_enabled" -> obj.updateEnabled(true)
        }
    }

    companion object {
        fun createDefault() = OSCHookRegistry(mutableListOf())
    }
}