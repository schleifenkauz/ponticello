package xenakis.model

import hextant.context.Context
import xenakis.impl.ScWriter
import xenakis.impl.SuperColliderClient

abstract class AbstractSuperColliderObject : AbstractRenamableObject(), SuperColliderObject {
    protected open val functionName get() = "${superColliderName}_init"

    protected val client get() = context[SuperColliderClient]

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
    }

    override fun onAdded(context: Context) {
        client.run { addToServer() }
    }

    override fun sync(writer: ScWriter) {
        writer.run {
            freeServerObject()
            allocateServerObject()
        }
    }

    override fun sync() {
        client.run { sync(writer) }
    }

    protected fun redefine() {
        client.run {
            removeFromServer()
            addToServer()

        }
    }

    protected open fun ScWriter.addToServer() {
        appendBlock("$functionName = ") {
            allocateServerObject()
        }
        appendLine(";")
        +"$liveCycleType.add($functionName)"
        +"if (s.serverRunning) { $functionName.value }"
    }

    override fun ScWriter.freeServerObject() {
        +"if ($superColliderName != nil) { $superColliderName.free }"
    }

    protected open fun ScWriter.removeFromServer() {
        +"$liveCycleType.remove($functionName)"
        freeServerObject()
    }

    override fun onRemoved() {
        client.run {
            removeFromServer()
            +"$functionName = nil"
            +"$superColliderName = nil"
        }
        initialized = false
    }

    override fun rename(newName: String) {
        val oldFunctionName = functionName
        val oldVariableName = superColliderName
        super.rename(newName)
        client.run {
            +"$functionName = $oldFunctionName"
            +"$superColliderName = $oldVariableName"
            +"$oldFunctionName = nil"
            +"$oldVariableName = nil"
        }
    }

}