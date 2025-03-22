package xenakis.model.registry

import hextant.context.Context
import kotlinx.serialization.Serializable
import xenakis.model.obj.GlobalPatternObject
import xenakis.model.obj.SuperColliderObject

@Serializable
class GlobalPatternRegistry(
    override val objects: MutableList<GlobalPatternObject> = mutableListOf()
) : SuperColliderObjectRegistry<GlobalPatternObject>() {
    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.InterpreterBoot

    override val objectType: String
        get() = "Pattern"

    override fun initialize(context: Context) {
        super.initialize(context)
    }

    companion object {
        fun createDefault() = GlobalPatternRegistry()
    }
}