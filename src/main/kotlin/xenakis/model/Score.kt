package xenakis.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import xenakis.impl.Point
import xenakis.impl.ScWriter
import xenakis.ui.format

@Serializable
data class Score(
    private var _totalDuration: Double,
    private val _objects: MutableSet<ScoreObject> = mutableSetOf(),
    private val _horizontalGroups: MutableList<MutableSet<String>> = mutableListOf(),
    private val _verticalGroups: MutableList<MutableSet<String>> = mutableListOf(),
) {
    val objects: Set<ScoreObject> get() = _objects

    @Transient
    private val objectsByName = objects.associateByTo(mutableMapOf()) { obj -> obj.name }

    @Transient
    private val horizontalGroups = mutableMapOf<ScoreObject, MutableSet<ScoreObject>>()

    @Transient
    private val verticalGroups = mutableMapOf<ScoreObject, MutableSet<ScoreObject>>()

    @Transient
    private val listeners = mutableListOf<ScoreListener>()

    var totalDuration: Double
        get() = _totalDuration
        set(value) {
            _totalDuration = value
            listeners.forEach { l -> l.setTotalDuration(value) }
        }

    init {
        for (group in _horizontalGroups) {
            val objects = group.mapTo(mutableSetOf()) { name -> getObject(name) }
            for (obj in objects) {
                horizontalGroups[obj] = objects
            }
        }
        for (group in _verticalGroups) {
            val objects = group.mapTo(mutableSetOf()) { name -> getObject(name) }
            for (obj in objects) {
                verticalGroups[obj] = objects
            }
        }
    }

    fun initialize(project: XenakisProject) {
        for (obj in objects) {
            obj.initialize(project)
        }
    }

    fun addListener(listener: ScoreListener) {
        listeners.add(listener)
    }

    private inline fun listeners(update: ScoreListener.() -> Unit) {
        listeners.forEach(update)
    }

    fun addObject(obj: ScoreObject) {
        _objects.add(obj)
        listeners.forEach { l -> l.addedObject(obj) }
        objectsByName[obj.name] = obj
    }

    fun removeObject(obj: ScoreObject) {
        _objects.remove(obj)
        listeners.forEach { l -> l.removedObject(obj) }
        objectsByName.remove(obj.name)
    }

    fun isNameAvailable(name: String) = name !in objectsByName

    fun getObject(name: String): ScoreObject = objectsByName[name] ?: error("no object '$name'")

    fun renameObject(obj: ScoreObject, newName: String) {
        check(isNameAvailable(newName))
        objectsByName.remove(obj.name)
        objectsByName[newName] = obj
        for (group in _verticalGroups + _horizontalGroups) {
            group.remove(obj.name)
            group.add(newName)
        }
        obj.name = newName
    }

    fun nameForClone(obj: ScoreObject): String {
        var name = obj.name
        while (true) {
            name += "_copy"
            if (isNameAvailable(name)) return name
        }
    }

    fun moveObject(obj: ScoreObject, newStart: Double, newY: Double) {
        val dx = newStart - obj.start
        val dy = newY - obj.y
        for (o in verticalGroups[obj] ?: setOf(obj)) {
            o.y += dy
            listeners { movedObject(o) }
        }
        for (o in horizontalGroups[obj] ?: setOf(obj)) {
            o.start += dx
            listeners { movedObject(o) }
        }
    }

    fun addVerticalGroup(objects: Collection<ScoreObject>) {
        addGroup(objects.toMutableSet(), verticalGroups, _verticalGroups)
    }

    fun addHorizontalGroup(objects: Collection<ScoreObject>) {
        addGroup(objects.toMutableSet(), horizontalGroups, _horizontalGroups)
    }

    private fun addGroup(
        objects: MutableSet<ScoreObject>,
        groups: MutableMap<ScoreObject, MutableSet<ScoreObject>>,
        _groups: MutableList<MutableSet<String>>
    ) {
        for (obj in objects) groups[obj]?.let { group -> objects.addAll(group) }
        for (obj in objects) groups[obj] = objects
        val itr = _groups.iterator()
        for (group in itr) {
            if (objects.any { obj -> obj.name in group }) itr.remove()
        }
        _groups.add(objects.mapTo(mutableSetOf()) { obj -> obj.name })
    }

    fun addTime(location: Double, amount: Double) {
        TODO("Not yet implemented")
    }

    fun writePlayerTask(writer: ScWriter, startTime: Double) {
        writer.appendLine("(~player_task = Task {")
        val relevantObjects = objects.filter { it.start + it.duration > startTime }
        val starts = relevantObjects.map { obj ->
            val start = obj.start.coerceAtLeast(startTime)
            val offset = (startTime - obj.start).coerceAtLeast(0.0)
            start to { obj.writeStartCode(writer, offset) }
        }
        val stops = relevantObjects.map { obj -> obj.start + obj.duration to { obj.writeStopCode(writer) } }
        val timedActions = (starts + stops).sortedBy { (t, _) -> t }
        val (t0, action0) = timedActions.first()
        val d = t0 - startTime
        writer.appendLine("${d.format(2)}.wait;")
        action0.invoke()
        timedActions.zipWithNext { (t1, _), (t2, action) ->
            val d = t2 - t1
            writer.appendLine("${d.format(2)}.wait;")
            action.invoke()
        }
        writer.appendLine("}.play)")
    }
}