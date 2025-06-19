package ponticello.model.live

import fxutils.drag.TypedDataFormat
import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import ponticello.model.obj.LiveTaskReference
import ponticello.model.obj.SuperColliderObject
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.run
import ponticello.sc.editor.CodeBlockEditor
import reaktive.value.now

@Serializable
class LiveTaskObject(
    val code: EditorRoot<@Contextual CodeBlockEditor>,
) : LiveObject(), SuperColliderObject {
    private val client get() = context[SuperColliderClient]

    override val superColliderName get() = "Tdef(\\${name.now})"

    override val canCopy: Boolean get() = true

    override val registry: LiveTaskRegistry
        get() = context[LiveTaskRegistry]

    override val quantization: Quantization
        get() = Quantization.None //TODO

    override fun initialize(context: Context) {
        super.initialize(context)
        code.initialize(context)
    }

    override fun rename(newName: String) {
        //TODO could this be done without killing the task?
        client.run { freeObject() }
        super.rename(newName)
        client.run { createObject() }
        if (isActive.now) doActivate()
    }

    override fun ScWriter.freeObject() {
        +"$superColliderName.clear"
    }

    override fun sync() {
        client.run { createObject() }
    }

    override fun copy() = LiveTaskObject(code.clone())

    override fun ScWriter.createObject() {
            appendBlock(superColliderName) {
                val body = code.editor.result.now
                body.writeCode(writer, context)
            }
    }

    override fun onRemoved() {
        super<LiveObject>.onRemoved()
        client.run { freeObject() }
    }

    override fun onLoadedIntoRegistry() {
        super<LiveObject>.onLoadedIntoRegistry()
        client.run { createObject() }
    }

    override fun doActivate() {
        client.run("$superColliderName.resume; $superColliderName.play")
    }

    override fun doDeactivate() {
        client.run("$superColliderName.pause")
    }

    override fun doReset() {
        client.run("$superColliderName.stop")
    }

    companion object {
        val DATA_FORMAT = TypedDataFormat<LiveTaskReference>("ponticello/live-task")
    }
}