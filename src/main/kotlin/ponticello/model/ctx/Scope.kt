package ponticello.model.ctx

import bundles.PublicProperty
import bundles.publicProperty
import kollektion.Counter
import ponticello.model.registry.ObjectList
import reaktive.Observer
import reaktive.collection.observeCollection
import reaktive.list.ReactiveList
import reaktive.value.*
import reaktive.value.binding.or
import java.util.*

class Scope private constructor(
    private val parent: Scope? = null
) {
    private var observer: Any? = null

    private val boundVariables = mutableMapOf<BoundVariable, Observer>()
    private val boundVariableNames = Counter<String>()
    private val queries = WeakHashMap<ReactiveString, Query>()

    fun isResolved(name: ReactiveString): ReactiveBoolean {
        val resolved = queries.getOrPut(name) {
            val resolved = reactiveVariable(name.now in boundVariableNames)
            val observer = name.observe { _, _, str -> resolved.set(str in boundVariableNames) }
            Query(observer, resolved)
        }.isResolved
        return if (parent == null) resolved
        else resolved or parent.isResolved(name)
    }

    fun boundVariables(): Set<BoundVariable> {
        val set = mutableSetOf<BoundVariable>()
        var scope: Scope? = this
        while (scope != null) {
            set.addAll(scope.boundVariables.keys)
            scope = scope.parent
        }
        return set
    }

    fun add(variable: BoundVariable) {
        if (boundVariables.containsKey(variable)) return
        boundVariableNames.add(variable.name.now)
        boundVariables[variable] = variable.name.observe { _, old, new ->
            boundVariableNames.remove(old)
            boundVariableNames.add(new)
        }
    }

    fun remove(variable: BoundVariable) {
        val observer = boundVariables.remove(variable)
        if (observer != null) {
            observer.kill()
            boundVariableNames.remove(variable.name.now)
        }
    }

    private data class Query(val observer: Observer, val isResolved: ReactiveVariable<Boolean>)

    private class ScopeListener<O>(
        private val scope: Scope,
        private val getVariable: (O) -> BoundVariable
    ) : ObjectList.Listener<O> {
        override fun added(obj: O, idx: Int) {
            scope.add(getVariable(obj))
        }

        override fun removed(obj: O, idx: Int) {
            scope.remove(getVariable(obj))
        }
    }

    abstract class BoundVariable {
        abstract val origin: Any

        abstract val name: ReactiveString

        abstract val info: ReactiveString
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BoundVariable

            return origin == other.origin
        }

        override fun hashCode(): Int {
            return origin.hashCode()
        }
    }

    companion object : PublicProperty<Scope> by publicProperty("Scope") {
        fun createEmpty(parent: Scope? = null) = Scope(parent)

        inline fun buildScope(parent: Scope? = null, block: Scope.() -> Unit) = createEmpty(parent).apply(block)

        fun <O> fromList(
            list: ObjectList<O>, parent: Scope? = null, getVariable: (O) -> BoundVariable
        ) = buildScope(parent) {
            val listener = ScopeListener(this, getVariable)
            list.addListener(listener)
            observer = listener
        }

        fun <O> fromList(list: ReactiveList<O>, parent: Scope? = null, getVariable: (O) -> BoundVariable) =
            buildScope(parent) {
                for (el in list.now) {
                    add(getVariable(el))
                }
                observer = list.observeCollection(
                    added = { _, el -> add(getVariable(el)) },
                    removed = { _, el -> remove(getVariable(el)) }
                )
            }

        fun constant(variables: Collection<BoundVariable>) = buildScope {
            for (variable in variables) {
                add(variable)
            }
        }

        fun constant(vararg variables: BoundVariable) = constant(variables.asList())
    }
}