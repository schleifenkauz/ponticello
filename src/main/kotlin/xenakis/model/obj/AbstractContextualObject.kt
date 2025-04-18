package xenakis.model.obj

import bundles.PublicProperty
import hextant.context.Context
import kotlinx.serialization.Transient
import xenakis.model.registry.NamedObject
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ObjectRegistry
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

abstract class AbstractContextualObject : ContextualObject {
    @Transient
    protected var initialized = false

    @Transient
    private lateinit var _context: Context

    final override val context: Context get() = _context

    protected fun setContext(context: Context) {
        _context = context
    }

    override fun initialize(context: Context) {
        //check(initialized) { "$this already initialized!" }
        if (initialized) return
        initialized = true
        _context = context
    }

    protected fun <T : NamedObject> (() -> ObjectReference<T>).ref(
        registry: PublicProperty<out ObjectRegistry<T>>
    ): ReadOnlyProperty<Any?, T?> = ReferencedObject(this, registry)

    private class ReferencedObject<T : NamedObject>(
        private val reference: () -> ObjectReference<T>,
        private val registry: PublicProperty<out ObjectRegistry<T>>
    ) : ReadOnlyProperty<Any?, T?> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T? = reference().get()

        fun resolve(context: Context) {
            reference().resolve(context[registry])
        }
    }
}