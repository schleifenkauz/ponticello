package xenakis.model.live

import kotlinx.serialization.Serializable
import xenakis.model.registry.ObjectRegistry

@Serializable
class LiveLoopRegistry(override val objects: MutableList<LiveLoopObject>) : ObjectRegistry<LiveLoopObject>() {
    constructor(): this(mutableListOf())

    override val objectType: String
        get() = "Live Loop"

    override fun syncAll() {

    }
}