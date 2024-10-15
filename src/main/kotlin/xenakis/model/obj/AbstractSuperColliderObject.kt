package xenakis.model.obj

import hextant.context.Context
import xenakis.model.obj.SuperColliderObject.LiveCycleType
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient

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

    override fun ScWriter.addToServer() {
        if (liveCycleType != LiveCycleType.InterpreterBoot) {
            appendBlock("$functionName = ") {
                allocateServerObject()
            }
            +"$liveCycleType.add($functionName)"
            +"if (s.serverRunning) { $functionName.value }"
        } else {
            allocateServerObject()
        }
    }

    override fun ScWriter.freeServerObject() {
        +"if ($superColliderName != nil) { $superColliderName.free }"
    }

    protected open fun ScWriter.removeFromServer() {
        if (liveCycleType != LiveCycleType.InterpreterBoot) +"$liveCycleType.remove($functionName)"
        freeServerObject()
    }

    override fun onRemoved() {
        client.run {
            removeFromServer()
            if (liveCycleType != LiveCycleType.InterpreterBoot) +"$functionName = nil"
            +"$superColliderName = nil"
        }
        initialized = false
    }

    override fun rename(newName: String) {
        val oldFunctionName = functionName
        val oldVariableName = superColliderName
        super.rename(newName)
        client.run {
            if (liveCycleType != LiveCycleType.InterpreterBoot) +"$functionName = $oldFunctionName"
            +"$superColliderName = $oldVariableName"
            +"$oldFunctionName = nil"
            +"$oldVariableName = nil"
        }
    }

}