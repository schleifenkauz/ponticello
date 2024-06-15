package xenakis.model

import hextant.context.Context
import reaktive.value.now
import xenakis.impl.ScWriter
import xenakis.impl.SuperColliderClient

abstract class AbstractSuperColliderObject : AbstractRenamableObject(), SuperColliderObject {
    abstract val variableName: String

    private val functionName get() = "~init_${name.now}"

    protected val client get() = context[SuperColliderClient]

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
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
        +"if ($variableName != nil) { $variableName.free }"
    }

    protected open fun ScWriter.removeFromServer() {
        +"$liveCycleType.remove($functionName)"
        freeServerObject()
    }

    override fun onRemoved() {
        client.run {
            removeFromServer()
            +"$functionName = nil;"
            +"$variableName = nil;"
        }
        initialized = false
    }

    override fun rename(newName: String) {
        val oldFunctionName = functionName
        val oldVariableName = variableName
        super.rename(newName)
        client.run {
            +"$functionName = $oldFunctionName"
            +"$variableName = $oldVariableName"
            +"$oldFunctionName = nil"
            +"$oldVariableName = nil"
        }
    }

}