package xenakis.model

import xenakis.impl.ScWriter
import xenakis.sc.Identifier

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
        ServerBoot, ServerTree;
    }
}