package ponticello.model.live

import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ponticello.impl.Decimal
import ponticello.impl.zero
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import ponticello.sc.editor.CodeBlockEditor
import reaktive.value.ReactiveVariable
import reaktive.value.now

@Serializable
class LiveTaskObject(
    val code: EditorRoot<@Contextual CodeBlockEditor>,
) : AbstractLiveObject() {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    private val client get() = context[SuperColliderClient]

    private val superColliderName get() = "Tdef(\\${name.now})"

    override fun initialize(context: Context) {
        super.initialize(context)
        code.initialize(context)
    }

    override fun rename(newName: String) {
        //TODO could this be done without killing the task?
        client.run {
            +"$superColliderName.clear"
        }
        super.rename(newName)
        client.run {
            appendBlock(superColliderName) {
                val body = code.editor.result.now
                body.writeCode(writer, context)
            }
        }
        if (isScheduled.now) doActivate(delay = zero)
    }

    fun sync() {
        client.run {
            appendBlock(superColliderName) {
                val body = code.editor.result.now
                body.writeCode(writer, context)
            }
        }
    }

    override fun copy() = LiveTaskObject(code.clone())

    override fun activate() {
        super.activate()
        sync()
    }

    override fun deactivate() {
        super.deactivate()
        client.run {
            +"$superColliderName.clear"
        }
    }

    override fun doActivate(delay: Decimal) {
        client.run("$superColliderName.resume; $superColliderName.play")
    }

    override fun doDeactivate() {
        client.run("$superColliderName.pause")
    }

    override fun doReset() {
        client.run("$superColliderName.stop")
    }
}