package xenakis.model

import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.context.withoutUndo
import hextant.core.editor.ListenerManager
import hextant.undo.UndoManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveString
import reaktive.value.now
import xenakis.impl.Point
import xenakis.ui.ScoreObjectSelector
import java.util.logging.Logger

@Serializable
class Score(
    private val _objects: MutableList<ScoreObject> = mutableListOf(),
    private val _horizontalGroups: MutableList<MutableSet<String>> = mutableListOf(),
    private val _verticalGroups: MutableList<MutableSet<String>> = mutableListOf(),
) {
    val objects: List<ScoreObject> get() = _objects

    lateinit var scoreName: ReactiveString
        private set

    @Transient
    lateinit var context: Context
        private set

    fun setContext(context: Context) {
        this.context = context
    }

    @Transient
    private var initialized = false

    @Transient
    val layoutManager = LayoutManager(_horizontalGroups, _verticalGroups)

    @Transient
    private val views = ListenerManager.createWeakListenerManager<ScoreListener>()

    private val undo by lazy { context[UndoManager] }

    fun initialize(context: Context, scoreName: ReactiveString) {
        if (initialized) return
        this.scoreName = scoreName
        setContext(context)
        if (scoreName.now == ROOT_SCORE_NAME) context[rootScore] = this
        for (obj in objects) {
            obj.initialize(context)
            obj.addToScore(this)
        }
        layoutManager.initialize(this)
    }

    fun addListener(listener: ScoreListener) {
        views.addListener(listener)
        for (obj in objects) {
            listener.addedObject(obj)
        }
        for (obj in objects) {
            val nxt = obj.nextInChain
            if (nxt != null) listener.chained(obj, nxt.get())
        }
    }

    fun has(name: String) = objects.any { obj -> obj !is ClonedObject && obj.name.now == name }

    fun getObject(name: String) =
        objects.find { obj -> obj !is ClonedObject && obj.name.now == name }
            ?: error("ScoreObject $name not found in ${scoreName.now}")

    fun getSubScore(name: String): Score {
        val obj = getObject(name)
        val group = obj as? ScoreObjectGroup ?: error("$obj is not a sub score")
        return group.score
    }

    fun copy() = Score(objects.mapTo(mutableListOf()) { o -> o.copy(o.name.now) }, _horizontalGroups, _verticalGroups)

    fun addObject(obj: ScoreObject) {
        if (obj !is ClonedObject && has(obj.name.now)) error("Name clash: $obj")
        obj.initialize(context)
        logger.info("Adding object ${obj.name.now} ${obj.position}")
        obj.addToScore(this)
        _objects.add(obj)
        views.notifyListeners { addedObject(obj) }
        undo.record(ScoreEdit.AddObject(obj, this))
    }

    fun removeObjects(set: Set<ScoreObject>) {
        val objectsAndTheirClones = objects.filterTo(mutableSetOf()) { o -> o is ClonedObject && o.original in set }
        objectsAndTheirClones.addAll(set)
        for (o in objectsAndTheirClones) {
            logger.info("Removing ${o.name.now}")
            _objects.remove(o)
            views.notifyListeners { removedObject(o) }
            layoutManager.removedObject(o)
        }
        undo.record(ScoreEdit.RemoveObjects(objectsAndTheirClones, this))
    }

    fun removeObject(obj: ScoreObject) {
        removeObjects(setOf(obj))
    }

    fun moveObject(obj: ScoreObject, newStart: Double, newY: Double) {
        val oldPos = Point(obj.start, obj.y)
        val newPos = Point(newStart, newY)
        if (oldPos == newPos) return
        undo.record(ScoreEdit.MoveObject(obj, oldPos, newPos, this))
        val dt = newStart - obj.start
        val dy = newY - obj.y
        layoutManager.moveObject(obj, dt, dy, context[ScoreObjectSelector].selectedObjects)
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
            val new = ClonedObject(copy)
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
        context.withoutUndo {
            for (obj in objects) {
                if (obj.start > location) {
                    val newStart = obj.start + amount
                    moveObject(obj, newStart, obj.y)
                }
            }
        }
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

    fun loop(obj: ScoreObject, period: Double, repetitions: Int, chainArrows: Boolean) {
        context[UndoManager].beginCompoundEdit("Loop object")
        var t = obj.start
        val layers = (obj.duration / period + 0.95).toInt()
        var prev = obj
        for (n in 1..repetitions) {
            t += period
            val layer = n % layers
            val y = obj.y + (layer * obj.height)
            val clone = obj.clone()
            clone.position.set(t, y)
            addObject(clone)
            if (chainArrows) chain(prev, clone)
            prev = clone
        }
        context[UndoManager].finishCompoundEdit()
    }

    fun availableName(prefix: String): String {
        for (n in 1..Int.MAX_VALUE) {
            val name = "${prefix}$n"
            if (!has(name)) return name
        }
        throw AssertionError()
    }

    fun nameForCopy(obj: ScoreObject): String {
        val name = obj.name.now
        val prefix = name.dropLastWhile { it.isDigit() }
        return availableName(prefix)
    }

    fun hasClonesOf(obj: ScoreObject): Boolean {
        if (obj is ClonedObject) return false
        for (o in objects) {
            if (o is ClonedObject && o.original == obj) return true
            if (o is ScoreObjectGroup && o.score.hasClonesOf(obj)) return true
        }
        return false
    }

    companion object {
        val rootScore = publicProperty<Score>("root-score")

        const val ROOT_SCORE_NAME = "<root>"

        private val logger = Logger.getLogger("Score")
    }
}