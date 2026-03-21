package ponticello.model.registry

class IdProvider<O : Any>() : ObjectList.Listener<O> {
    private var idCounter = 0

    private val objectsById: MutableMap<Int, O> = mutableMapOf()
    private val objectIds: MutableMap<O, Int> = mutableMapOf()

    constructor(list: ObjectList<O>) : this() {
        list.addListener(this)
    }

    override fun added(obj: O, idx: Int) {
        val id = idCounter++
        objectsById[id] = obj
        objectIds[obj] = id
    }

    override fun removed(obj: O, idx: Int) {
        val id = objectIds.remove(obj) ?: return
        objectsById.remove(id)
    }

    fun getById(id: Int): O? = objectsById[id]

    fun getId(obj: O): Int? = objectIds[obj]
}