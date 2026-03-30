package ponticello.model.ctx

import bundles.PublicProperty
import bundles.publicProperty
import ponticello.model.registry.ObjectList
import ponticello.sc.Identifier
import ponticello.sc.client.SuperColliderClassList
import reaktive.Observer
import reaktive.collection.observeCollection
import reaktive.list.ReactiveList
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.binding.orElse
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.util.*

class Scope private constructor(private val parent: Scope? = null) {
    private var observer: Any? = null

    private val boundVariableObservers = mutableMapOf<BoundVariable, Observer>()
    private val boundVariables = mutableMapOf<String, MutableSet<BoundVariable>>()
    private val queries = WeakHashMap<ReactiveValue<String?>, Query>()

    fun resolve(identifier: ReactiveValue<String?>): ReactiveValue<BoundVariable?> {
        val resolved = queries.getOrPut(identifier) {
            val resolved = reactiveVariable(resolveNow(identifier.now))
            val observer = identifier.observe { _, _, str -> resolved.set(resolveNow(str)) }
            Query(observer, resolved)
        }.resolution
        return if (parent == null) resolved
        else resolved.orElse(parent.resolve(identifier))
    }

    private fun resolveNow(str: String?): BoundVariable? = when {
        str == null -> null
        !(Identifier.isValid(str)) -> null
        else -> boundVariables[str]?.maxByOrNull(BoundVariable::priority)
    }

    fun boundVariables(): Set<BoundVariable> {
        val set = mutableSetOf<BoundVariable>()
        var scope: Scope? = this
        while (scope != null) {
            set.addAll(scope.boundVariableObservers.keys)
            scope = scope.parent
        }
        return set
    }

    fun add(variable: BoundVariable) {
        if (boundVariableObservers.containsKey(variable)) return
        addVariable(variable.name.now, variable)
        boundVariableObservers[variable] = variable.name.observe { _, oldName, newName ->
            removeVariable(oldName, variable)
            addVariable(newName, variable)
        }
    }

    private fun addVariable(name: String, variable: BoundVariable) {
        val variables = boundVariables.getOrPut(name, ::mutableSetOf)
        if (variables.add(variable)) {
            for ((identifier, query) in queries) {
                if (identifier.now == name) {
                    query.resolution.set(variables.maxByOrNull(BoundVariable::priority))
                }
            }
        }
    }

    private fun removeVariable(name: String, variable: BoundVariable) {
        val variables = boundVariables[name] ?: return
        if (variables.remove(variable)) {
            for ((identifier, query) in queries) {
                if (identifier.now == name) {
                    query.resolution.set(variables.maxByOrNull(BoundVariable::priority))
                }
            }
        }
    }

    fun remove(variable: BoundVariable) {
        val observer = boundVariableObservers.remove(variable)
        if (observer != null) {
            observer.kill()
            removeVariable(variable.name.now, variable)
        }
    }

    private data class Query(val observer: Observer, val resolution: ReactiveVariable<BoundVariable?>)

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

    companion object : PublicProperty<Scope> by publicProperty("Scope") {
        fun createEmpty(parent: Scope? = null) = Scope(parent)

        inline fun buildScope(parent: Scope? = null, block: Scope.() -> Unit): Scope = createEmpty(parent).apply(block)

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

        fun constant(parent: Scope?, variables: Collection<BoundVariable>) = buildScope(parent) {
            for (variable in variables) {
                add(variable)
            }
        }

        fun constant(parent: Scope?, vararg variables: BoundVariable) = constant(parent, variables.asList())

        fun root(classList: SuperColliderClassList) = buildScope {
            for (className in classList.getClassNames()) {
                add(SuperColliderClass(className))
            }
        }
    }
}