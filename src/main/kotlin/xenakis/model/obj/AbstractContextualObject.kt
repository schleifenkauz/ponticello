package xenakis.model.obj

import bundles.PublicProperty
import hextant.context.Context
import kotlinx.serialization.Transient
import xenakis.model.registry.NamedObject
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ObjectRegistry
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

abstract class AbstractContextualObject : ContextualObject {
    @Transient
    protected var initialized = false

    private val references = mutableSetOf<ReferencedObject<*>>()

    @Transient
    private lateinit var _context: Context

    final override val context: Context get() = _context

    override fun initialize(context: Context) {
        if (initialized) return
        initialized = true
        _context = context
        for (reference in references) {
            reference.resolve(context)
        }
    }

    protected inline fun <reified T : NamedObject> (() -> ObjectReference).ref(
        registry: PublicProperty<out ObjectRegistry<T>>
    ): ReadOnlyProperty<Any?, T> = referencedObject(registry, T::class)

    protected fun <T : NamedObject> (() -> ObjectReference).referencedObject(
        registry: PublicProperty<out ObjectRegistry<T>>, cls: KClass<T>
    ): ReadOnlyProperty<Any?, T> = ReferencedObject(this, cls, registry)

    private class ReferencedObject<T : NamedObject>(
        private val reference: () -> ObjectReference,
        private val cls: KClass<T>,
        private val registry: PublicProperty<out ObjectRegistry<T>>
    ) : ReadOnlyProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = reference().get(cls)

        fun resolve(context: Context) {
            reference().resolve(context[registry])
        }
    }
}