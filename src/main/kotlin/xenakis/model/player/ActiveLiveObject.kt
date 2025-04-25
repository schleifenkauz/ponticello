package xenakis.model.player

import reaktive.value.now
import xenakis.model.live.LiveObject
import xenakis.model.registry.NamedObject

class ActiveLiveObject(val obj: LiveObject): ActiveObject() {
    override val associatedObject: NamedObject
        get() = obj
    override val uniqueName: String
        get() = obj.name.now + "_live"
    override val superColliderName: String
        get() = obj.superColliderName
}