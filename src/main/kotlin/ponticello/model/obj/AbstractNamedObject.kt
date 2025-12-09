package ponticello.model.obj

import reaktive.value.now

abstract class AbstractNamedObject : AbstractContextualObject(), NamedObject {
    override fun toString(): String {
        val name = try {
            "#${name.now}"
        } catch (e: IllegalStateException) { //no name available
            "<unnamed>"
        }
        return "${javaClass.simpleName} $name"
    }
}