package xenakis.model.registry

import xenakis.model.obj.MeterObject

class MeterRegistry(override val objects: MutableList<MeterObject>) : ObjectRegistry<MeterObject>() {
    override val objectType: String
        get() = "Meter"

    override fun syncAll() {}
}