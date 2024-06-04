package xenakis.model

import hextant.context.Context
import hextant.core.editor.ListenerManager
import hextant.undo.UndoManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.now
import xenakis.impl.Point
import xenakis.impl.ScWriter
import xenakis.ui.format
import kotlin.math.ceil

@Serializable
class Score(
    private val _objects: MutableList<ScoreObject> = mutableListOf(),
    private val _horizontalGroups: MutableList<MutableSet<String>> = mutableListOf(),
    private val _verticalGroups: MutableList<MutableSet<String>> = mutableListOf(),
) {
    val objects: MutableList<ScoreObject> get() = _objects

    @Transient
    lateinit var context: Context
        private set

    @Transient
    val layoutManager = LayoutManager(_horizontalGroups, _verticalGroups)

    @Transient
    private val views = ListenerManager.createWeakListenerManager<ScoreListener>()

    private val objectRegistry by lazy { context[ScoreObjectRegistry] }

    private val undo by lazy { context[UndoManager] }

    fun initialize(context: Context) {
        this.context = context
        for (obj in objects) {
            objectRegistry.add(obj)
        }
        for (obj in objects) {
            obj.initialize(context)
            obj.addToScore(this)
        }
        layoutManager.initialize(context)
    }

    fun addListener(listener: ScoreListener) {
        views.addListener(listener)
        for (obj in objects) {
            listener.addedObject(obj)
            val nxt = obj.nextInChain
            if (nxt != null) listener.chained(obj, nxt.get())
        }
    }

    fun addObject(obj: ScoreObject) {
        obj.initialize(context)
        obj.addToScore(this)
        _objects.add(obj)
        objectRegistry.add(obj)
        views.notifyListeners { addedObject(obj) }
        undo.record(ScoreEdit.AddObject(obj, this))
    }

    fun removeObject(obj: ScoreObject) {
        val objAndItsClones = objects.filterIsInstance<ClonedObject>().filter { it.original == obj } + obj
        for (o in objAndItsClones) {
            _objects.remove(o)
            views.notifyListeners { removedObject(o) }
            objectRegistry.remove(o)
            layoutManager.removedObject(o)
            o.onRemove()
        }
        undo.record(ScoreEdit.RemoveObjects(objAndItsClones, this))
    }

    fun moveObject(obj: ScoreObject, newStart: Double, newY: Double) {
        undo.record(ScoreEdit.MoveObject(obj, Point(obj.start, obj.y), Point(newStart, newY), this))
        val dt = newStart - obj.start
        val dy = newY - obj.y
        layoutManager.moveObject(obj, dt, dy)
    }

    private fun chain(previous: ScoreObject, next: ClonedObject) {
        previous.nextInChain = next.createReference()
        views.notifyListeners { chained(previous, next) }
    }

    fun unchain(previous: ScoreObject) {
        val nxt = previous.nextInChain?.get() ?: return
        previous.nextInChain = null
        views.notifyListeners { unchained(previous, nxt) }
        val copy = nxt.copy(nxt.name.now)
        copy.position.set(nxt.position)
        replace(nxt, copy)
        var prev = copy
        var obj = nxt
        while (true) {
            obj = obj.nextInChain?.get() ?: break
            val new = ClonedObject(obj.name.now, original = copy)
            new.position.set(obj.position)
            replace(obj, new)
            chain(prev, new)
            prev = new
        }
    }

    private fun replace(old: ScoreObject, new: ScoreObject) {
        removeObject(old)
        addObject(new)
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

    fun writePlayerTask(writer: ScWriter, startTime: Double, taskName: String, prefix: String) {
        writer.appendLine("(~$taskName = Task {")
        val relevantObjects = objects.filter { obj -> !obj.muted && obj.start + obj.duration > startTime }
        val starts = relevantObjects.map { obj ->
            val start = obj.start.coerceAtLeast(startTime)
            val offset = (startTime - obj.start).coerceAtLeast(0.0)
            val name = "$prefix${obj.name.now}"
            start to { obj.writeStartCode(writer, offset, name) }
        }
        val stops = relevantObjects.map { obj ->
            val name = "$prefix${obj.name.now}"
            obj.start + obj.duration to { obj.writeStopCode(writer, name) }
        }
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

    fun loop(obj: ScoreObject, period: Double, repetitions: Int) {
        context[UndoManager].beginCompoundEdit("Loop object")
        var t = obj.start
        val layers = ceil(obj.duration / period).toInt()
        var prev = obj
        for (n in 1..repetitions) {
            t += period
            val layer = n % layers
            val y = obj.y + (layer * obj.height)
            val clone = obj.clone("${obj.name.now}_loop$n")
            clone.position.set(t, y)
            addObject(clone)
            chain(prev, clone)
            prev = clone
        }
        context[UndoManager].finishCompoundEdit()
    }

    fun copy(): Score {
        val copies = mutableMapOf<ScoreObject, ScoreObject>()
        for (obj in objects) {
            val copy =
                if (obj is ClonedObject) {
                    val original = copies[obj.original] ?: error("original for clone $obj not found!")
                    original.clone(objectRegistry.nameForClone(original))
                } else obj.copy(objectRegistry.nameForCopy(obj))
            copies[obj] = copy
        }
        for (obj in copies.values) {
            val nxtInChain = obj.nextInChain?.get() ?: continue
            obj.nextInChain = copies.getValue(nxtInChain).createReference()
        }
        val horizontalGroups = _horizontalGroups.mapTo(mutableListOf()) { g ->
            g.mapTo(mutableSetOf()) { name ->
                copies.getValue(objectRegistry.get(name)).name.now
            }
        }
        val verticalGroups = _verticalGroups.mapTo(mutableListOf()) { g ->
            g.mapTo(mutableSetOf()) { name ->
                copies.getValue(objectRegistry.get(name)).name.now
            }
        }
        return Score(copies.values.toMutableList(), horizontalGroups, verticalGroups)
    }
}