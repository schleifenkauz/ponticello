package xenakis.model

import hextant.context.Context
import reaktive.value.now
import xenakis.model.LayoutManager.LayoutAspect.Horizontal
import xenakis.model.LayoutManager.LayoutAspect.Vertical

class LayoutManager(
    horizontalGroups: MutableList<MutableSet<String>>,
    verticalGroups: MutableList<MutableSet<String>>
) {
    private val groups = mapOf(Horizontal to horizontalGroups, Vertical to verticalGroups)
    private val LayoutAspect.groups get() = this@LayoutManager.groups.getValue(this)

    private val groupByObject = LayoutAspect.values()
        .associateWith { mutableMapOf<ScoreObject, MutableSet<ScoreObject>>() }
    private val LayoutAspect.groupByObject get() = this@LayoutManager.groupByObject.getValue(this)

    private fun LayoutAspect.group(obj: ScoreObject) = groupByObject[obj] ?: setOf(obj)

    fun initialize(context: Context) {
        val namingManager = context[ScoreObjectRegistry]
        eachAspect {
            for (group in groups) {
                val objects = group.mapTo(mutableSetOf()) { name -> namingManager.get(name) }
                for (obj in objects) groupByObject[obj] = objects
            }
        }
    }

    fun addGroup(aspect: LayoutAspect, selected: Collection<ScoreObject>) {
        val newGroup = selected.flatMapTo(mutableSetOf()) { obj -> aspect.group(obj) }
        for (obj in selected) aspect.groupByObject[obj] = newGroup
        val groups = groups[aspect]!!
        val itr = groups.iterator()
        for (group in itr) {
            if (newGroup.any { obj -> obj.name.now in group }) itr.remove()
        }
        groups.add(newGroup.mapTo(mutableSetOf()) { obj -> obj.name.now })
    }

    fun removeFromGroup(obj: ScoreObject, aspect: LayoutAspect) {
        val group = aspect.groupByObject.remove(obj) ?: return
        group.remove(obj)
        aspect.groups.forEach { g -> g.remove(obj.name.now) }
    }

    fun removedObject(obj: ScoreObject) = eachAspect {
        groupByObject[obj]?.remove(obj)
        groups.forEach { it.remove(obj.name.now) }
    }

    fun moveObject(obj: ScoreObject, dt: Double, dy: Double, selectedObjects: Set<ScoreObject>) {
        val vert = (Vertical.groupByObject[obj] ?: setOf(obj)) + selectedObjects
        val hor = (Horizontal.groupByObject[obj] ?: setOf(obj)) + selectedObjects
        for (o in vert) o.position.y += dy
        for (o in hor) o.position.start += dt
    }

    fun renamedObject(oldName: String, newName: String) = eachAspect {
        for (group in groups) {
            if (group.remove(oldName)) group.add(newName)
        }
    }

    enum class LayoutAspect {
        Horizontal, Vertical;
    }

    companion object {
        private inline fun eachAspect(action: LayoutAspect.() -> Unit) {
            for (aspect in LayoutAspect.values()) action(aspect)
        }
    }
}