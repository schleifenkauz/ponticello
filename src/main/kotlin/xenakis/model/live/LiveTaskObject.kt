package xenakis.model.live

import hextant.context.Context
import hextant.serial.EditorRoot
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.sc.client.ScWriter
import xenakis.sc.editor.CodeBlockEditor

class LiveTaskObject(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    val code: EditorRoot<@Contextual CodeBlockEditor>,
) : LiveObject() {
    override val superColliderName get() = "Tdef(\\${name.now})"

    override val canCopy: Boolean get() = true

    override val registry: LiveTaskRegistry
        get() = context[LiveTaskRegistry]

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

    override fun ScWriter.sync() {
        createObject()
    }

    override fun copy(name: String) = LiveTaskObject(reactiveVariable(name), code.clone())

    override fun ScWriter.createObject() {
        client.run {
            appendBlock(superColliderName) {
                val body = code.editor.result.now
                body.writeCode(writer, context)
            }
        }
    }

    override fun ScWriter.freeObject() {
        client.run("$superColliderName.clear")
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
}