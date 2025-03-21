package xenakis.ui.registry

import reaktive.list.MutableReactiveList
import xenakis.model.registry.NamedObject

abstract class ReactiveListSource<O: NamedObject>(protected val objects: MutableReactiveList<O>): ObjectBoxSource<O> {
    final override val items: List<O>
        get() = objects.now

    final override fun removeObject(obj: O) {
        objects.now.remove(obj)
    }

    final override fun addObject(obj: O, idx: Int) {
        objects.now.add(idx, obj)
    }
}