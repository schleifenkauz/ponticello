package xenakis.model

import hextant.context.Context
import kotlinx.serialization.Transient

abstract class AbstractObjectReference<O : NamedObject>(private var name: String) : ObjectReference<O> {
    @Transient
    protected var obj: O? = null

    override fun get(): O = obj ?: error("Object $name not resolved!")

    protected abstract fun getRegistry(context: Context): ObjectRegistry<O>

    override fun initialize(context: Context) {
        obj = getRegistry(context).get(name)
    }

}