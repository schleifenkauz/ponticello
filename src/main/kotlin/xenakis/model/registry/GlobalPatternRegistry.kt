package xenakis.model.registry

import hextant.context.Context
import kotlinx.serialization.Serializable
import xenakis.model.obj.GlobalPatternObject
import xenakis.model.obj.SuperColliderObject

@Serializable
class GlobalPatternRegistry(
    private val patterns: MutableList<GlobalPatternObject>
) : SuperColliderObjectRegistry<GlobalPatternObject>() {
    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.InterpreterBoot

    override val objects: MutableList<GlobalPatternObject>
        get() = patterns

    override val objectType: String
        get() = "Pattern"

    override fun initialize(context: Context) {
        super.initialize(context)
    }

    companion object {
        fun createDefault() = GlobalPatternRegistry(mutableListOf())
    }
}