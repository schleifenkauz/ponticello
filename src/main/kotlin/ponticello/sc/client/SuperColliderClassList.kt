package ponticello.sc.client

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import com.illposed.osc.OSCMessageEvent
import com.illposed.osc.OSCMessageListener
import hextant.context.Context
import ponticello.model.ctx.Scope
import java.util.concurrent.CompletableFuture

class SuperColliderClassList(private val onFinished: CompletableFuture<Unit>) : OSCMessageListener {
    private val classes = mutableSetOf<String>()

    override fun acceptMessage(event: OSCMessageEvent) {
        when (event.message.address) {
            "/classes_finished" -> {
                onFinished.complete(Unit)
            }

            "/classes" -> {
                val args = event.message.arguments
                classes.addAll(args.map { clsName -> clsName as String })
            }
        }
    }

    fun getClassNames(): Set<String> = classes

    companion object : PublicProperty<SuperColliderClassList> by publicProperty("ClassList") {
        fun query(context: Context) {
            val onFinished = CompletableFuture<Unit>()
            val list = SuperColliderClassList(onFinished)
            val client = context[SuperColliderClient]
            client.addListener(list)
            client.send("/send_classes")
            onFinished.join()
            context[SuperColliderClassList] = list
            context[Scope] = Scope.root(list)
        }
    }
}