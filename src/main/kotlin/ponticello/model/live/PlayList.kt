package ponticello.model.live

import ponticello.model.registry.NamedObjectList

class PlayList(override val objects: MutableList<PlayListItem>) : NamedObjectList<PlayListItem>() {
    override val objectType: String get() = "PlayListItem"


}