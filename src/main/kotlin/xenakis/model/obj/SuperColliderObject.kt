package xenakis.model.obj

import xenakis.model.registry.NamedObject
import xenakis.sc.Identifier
import xenakis.sc.client.ScWriter

interface SuperColliderObject : NamedObject {
    val superColliderName: String

    val superColliderExpr get() = Identifier(superColliderName)

    val liveCycleType: LiveCycleType

    fun ScWriter.allocateServerObject()

    fun ScWriter.freeServerObject()

    fun ScWriter.addToServer()

    fun sync(writer: ScWriter)

    fun sync()

    enum class LiveCycleType {
        InterpreterBoot, ServerBoot, ServerTree;
    }
}