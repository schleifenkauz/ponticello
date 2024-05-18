package xenakis.model

import hextant.context.Context
import hextant.core.editor.ViewManager
import hextant.undo.UndoManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import xenakis.impl.Point
import xenakis.impl.ScWriter
import xenakis.ui.format

@Serializable
data class Score(
    private val _objects: MutableList<ScoreObject> = mutableListOf(),
    private val _horizontalGroups: MutableList<MutableSet<String>> = mutableListOf(),
    private val _verticalGroups: MutableList<MutableSet<String>> = mutableListOf(),
) : ScoreObjectContainer() {
    val objects: MutableList<ScoreObject> get() = _objects

    @Transient
    lateinit var context: Context

    @Transient
    private val objectsByName = objects.associateByTo(mutableMapOf()) { obj -> obj.name }

    @Transient
    private val horizontalGroups = mutableMapOf<ScoreObject, MutableSet<ScoreObject>>()

    @Transient
    private val verticalGroups = mutableMapOf<ScoreObject, MutableSet<ScoreObject>>()

    @Transient
    private val views = ViewManager.createWeakViewManager<ScoreListener>()

    private val undo get() = context[UndoManager]

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

    fun addListener(listener: ScoreListener) {
        views.addView(listener)
        for (obj in objects) {
            listener.addedObject(obj)
        }
    }

    fun addObject(obj: ScoreObject) {
        obj.addToContainer(this, context)
        _objects.add(obj)
        views.notifyViews { addedObject(obj) }
        objectsByName[obj.name] = obj
        undo.record(ScoreEdit.AddObject(obj, this))
    }

    override fun removeObject(obj: ScoreObject) {
        val objects = objects.filterIsInstance<ClonedObject>().filter { it.original == obj } + obj
        for (o in objects) {
            _objects.remove(o)
            views.notifyViews { removedObject(o) }
            objectsByName.remove(o.name)
            _verticalGroups.all { g -> g.remove(o.name) }
            _horizontalGroups.all { g -> g.remove(o.name) }
            horizontalGroups.remove(o)
            verticalGroups.remove(o)
            horizontalGroups.values.all { it.remove(o) }
            verticalGroups.values.all { it.remove(o) }
            o.onRemove()
        }
        undo.record(ScoreEdit.RemoveObjects(objects, this))
    }

    fun isNameAvailable(name: String) = name !in objectsByName

    override fun getObject(name: String): ScoreObject = objectsByName[name] ?: error("no object '$name'")

    override fun renamedObject(obj: ScoreObject, oldName: String, newName: String) {
        objectsByName.remove(oldName)
        objectsByName[newName] = obj
        for (group in _verticalGroups + _horizontalGroups) {
            group.remove(obj.name)
            group.add(newName)
        }
    }

    fun nameForCopy(obj: ScoreObject): String {
        for (n in 1..Int.MAX_VALUE) {
            val name = "${obj.name}_copy$n"
            if (isNameAvailable(name)) return name
        }
        throw AssertionError()
    }

    fun nameForClone(obj: ScoreObject): String {
        for (n in 1..Int.MAX_VALUE) {
            val name = "${obj.name}_clone$n"
            if (isNameAvailable(name)) return name
        }
        throw AssertionError()
    }

    fun moveObject(obj: ScoreObject, newStart: Double, newY: Double) {
        undo.record(ScoreEdit.MoveObject(obj, Point(obj.start, obj.y), Point(newStart, newY), this))
        val dt = newStart - obj.start
        val dy = newY - obj.y
        for (o in verticalGroups[obj] ?: setOf(obj)) {
            o.position.y += dy
        }
        for (o in horizontalGroups[obj] ?: setOf(obj)) {
            o.position.start += dt
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
        undo.record(ScoreEdit.AddTime(location, amount, this))
        for (obj in objects) {
            if (obj.start > location) {
                val newStart = obj.start + amount
                moveObject(obj, newStart, obj.y)
            }
        }
    }

    fun recoloredSynthDef(name: String) {
        for (obj in objects) {
            if (obj is SynthObject && obj.synthDefName == name) {
                obj.associatedColor = obj.synthDef.associatedColor
            }
        }
    }

    fun writePlayerTask(writer: ScWriter, startTime: Double) {
        writer.appendLine("(~player_task = Task {")
        val relevantObjects = objects.filter { obj -> !obj.muted && obj.start + obj.duration > startTime }
        val starts = relevantObjects.map { obj ->
            val start = obj.start.coerceAtLeast(startTime)
            val offset = (startTime - obj.start).coerceAtLeast(0.0)
            start to { obj.writeStartCode(writer, offset) }
        }
        val stops = relevantObjects.map { obj -> obj.start + obj.duration to { obj.writeStopCode(writer) } }
        val timedActions = (starts + stops).sortedBy { (t, _) -> t }
        if (timedActions.isNotEmpty()) {
            val (t0, action0) = timedActions.first()
            val startDiff = t0 - startTime
            writer.appendLine("${startDiff.format(2)}.wait;")
            action0.invoke()
            timedActions.zipWithNext { (t1, _), (t2, action) ->
                val d = t2 - t1
                writer.appendLine("${d.format(2)}.wait;")
                action.invoke()
            }
        }
        writer.appendLine("}.play)")
    }

    fun deleteTimeRange(start: Double, end: Double) {
        undo.beginCompoundEdit()
        val removedDuration = end - start
        val removedObjects = mutableListOf<ScoreObject>()
        for (obj in objects.toSet()) {
            if (obj.start > start) {
                if (obj.start + obj.duration < end) {
                    removeObject(obj)
                    removedObjects.add(obj)
                } else {
                    val newStart = (obj.start - removedDuration).coerceAtLeast(0.0)
                    moveObject(obj, newStart, obj.y)
                }
            }
        }
        undo.finishCompoundEdit("Delete time range")
    }
}