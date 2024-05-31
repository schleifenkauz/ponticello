package xenakis.model

import hextant.context.Context
import kotlinx.serialization.Transient
import java.util.logging.Level
import java.util.logging.Logger

abstract class AbstractObjectReference<O : NamedObject>(private var name: String) : ObjectReference<O> {
    @Transient
    protected var obj: O? = null

    override fun get(): O = obj ?: error("Object $name not resolved!")

    protected abstract fun getRegistry(context: Context): ObjectRegistry<O>

    override fun resolve(context: Context) {
        if (obj != null) {
            logger.fine("ObjectReference '$name' already resolved")
            return
        }
        val registry = getRegistry(context)
        try {
            obj = registry.get(name)
        } catch (ex: NoSuchElementException) {
            logger.log(Level.SEVERE, ex.message, ex)
            obj = registry.getDefault()
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AbstractObjectReference<*>) return false

        if (name != other.name) return false
        if (obj != other.obj) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (obj?.hashCode() ?: 0)
        return result
    }

    companion object {
        private val logger = Logger.getLogger("ObjectReference")
    }
}