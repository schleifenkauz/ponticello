package xenakis.model

import xenakis.impl.ScWriter

interface SuperColliderObject : NamedObject {
    val liveCycleType: LiveCycleType

    fun ScWriter.allocateServerObject()

    fun ScWriter.freeServerObject()

    fun sync(writer: ScWriter)

    fun sync()

    enum class LiveCycleType {
        ServerBoot, ServerTree;
    }
}