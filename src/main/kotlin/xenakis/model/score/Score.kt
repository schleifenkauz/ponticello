package xenakis.model.score

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
import reaktive.value.reactiveValue
import xenakis.impl.*
import xenakis.model.obj.AbstractContextualObject
import xenakis.model.registry.ScoreObjectRegistry

@Serializable
class Score(
    private val instances: MutableList<ScoreObjectInstance> = mutableListOf()
) : AbstractContextualObject() {
    val objectInstances: List<ScoreObjectInstance> get() = instances

    val objects get() = objectInstances.mapTo(mutableSetOf()) { inst -> inst.obj }

    lateinit var scoreName: ReactiveString
        private set

    @Transient
    var parentObject: ScoreObjectGroup? = null
        private set

    @Transient
    private val views = ListenerManager.createWeakListenerManager<ScoreListener>()

    private val undo by lazy { context[UndoManager] }

    val maxTime get() = parentObject?.duration ?: Decimal.INF
    val maxY get() = parentObject?.height ?: one(ObjectPosition.Y_PRECISION)

    fun initialize(context: Context, parentObject: ScoreObjectGroup?) {
        super.initialize(context)
        this.parentObject = parentObject
        this.scoreName = parentObject?.name ?: reactiveValue(ROOT_SCORE_NAME)
        context[rootScore] = this
        for (inst in objectInstances) {
            inst.initialize(context)
        }
    }

    override fun initialize(context: Context) {
        initialize(context, parentObject = null)
        for (inst in objectInstances) {
            inst.addedToScore(this)
        }
    }

    fun addListener(listener: ScoreListener, notify: Boolean = true) {
        views.addListener(listener)
        if (notify) {
            for (inst in objectInstances) {
                listener.addedObject(this, inst)
            }
        }
    }

    fun removeListener(listener: ScoreListener) {
        views.removeListener(listener)
    }

    fun has(name: String) = objects.any { obj -> obj.name.now == name }

    fun deepClone() = Score(instances.mapTo(mutableListOf()) { inst ->
        val name = context[ScoreObjectRegistry].nameForClone(inst.obj)
        inst.clone(inst.position, name)
    })

    fun clone() = Score(instances.mapTo(mutableListOf()) { inst -> inst.duplicate(inst.position) })

    fun addObject(inst: ScoreObjectInstance) {
        inst.initialize(context)
        inst.addedToScore(this)
        Logger.info("Adding object ${inst.obj.name.now} at ${inst.position} to ${scoreName.now}", Logger.Category.Score)
        instances.add(inst)
        views.notifyListeners { addedObject(this@Score, inst) }
        undo.record(ScoreEdit.AddObject(inst, this))
    }

    fun removeObjects(set: Set<ScoreObjectInstance>, removeFromRegistry: Boolean) {
        for (inst in set) {
            Logger.info("Removing ${inst.obj.name.now} from score ${scoreName.now}", Logger.Category.Score)
            instances.remove(inst)
            views.notifyListeners { removedObject(this@Score, inst) }
            inst.removedFromScore(removeFromRegistry)
        }
        undo.record(ScoreEdit.RemoveObjects(set, removeFromRegistry, this))
    }

    fun removeObject(obj: ScoreObjectInstance, removeFromRegistry: Boolean) {
        removeObjects(setOf(obj), removeFromRegistry)
    }

    fun movedObject(inst: ScoreObjectInstance, oldPosition: ObjectPosition) {
        val dt = inst.start - oldPosition.time
        val dy = inst.y - oldPosition.y
        views.notifyListeners { movedObject(this@Score, inst, dt, dy) }
    }

    fun toggledMute(inst: ScoreObjectInstance, muted: Boolean) {
        views.notifyListeners { toggledMute(this@Score, inst, muted) }
    }

    fun addTime(location: Decimal, amount: Decimal) {
        undo.record(ScoreEdit.AddTime(location, amount, this))
        context.withoutUndo {
            for (inst in objectInstances) {
                if (inst.start > location) {
                    val newStart = inst.start + amount
                    inst.setTime(newStart)
                }
            }
        }
    }

    fun deleteTimeRange(start: Decimal, end: Decimal) {
        undo.beginCompoundEdit()
        val removedDuration = end - start
        for (inst in objectInstances) {
            if (inst.start > start) {
                if (inst.start + inst.obj.duration < end) {
                    removeObject(inst, removeFromRegistry = true)
                } else {
                    val newStart = (inst.start - removedDuration).coerceAtLeast(zero)
                    inst.setTime(newStart)
                }
            }
        }
        undo.finishCompoundEdit("Delete time range")
    }

    fun loop(inst: ScoreObjectInstance, period: Decimal, repetitions: Int) {
        context[UndoManager].beginCompoundEdit("Loop object")
        var t = inst.start
        val layers = (inst.obj.duration / period + 0.95).toInt()
        for (n in 1..repetitions) {
            t += period
            val layer = n % layers
            val y = inst.position.y + (layer * inst.height)
            val clone = inst.duplicate(t, y)
            addObject(clone)
        }
        context[UndoManager].finishCompoundEdit()
    }

    fun hasInstancesOf(obj: ScoreObject, filterMuted: Boolean = false): Boolean = instancesOf(obj, filterMuted).any()

    fun instancesOf(obj: ScoreObject, filterMuted: Boolean = false): Sequence<ScoreObjectInstance> = sequence {
        for (inst in objectInstances) {
            val o = inst.obj
            if (filterMuted && inst.muted.now) continue
            if (o == obj) yield(inst)
            else if (o is ScoreObjectGroup) yieldAll(o.score.instancesOf(obj, filterMuted))
        }
    }

    fun allInstances(): Sequence<ScoreObjectInstance> = sequence {
        for (inst in objectInstances) {
            val o = inst.obj
            yield(inst)
            if (o is ScoreObjectGroup) yieldAll(o.score.allInstances())
        }
    }

    companion object {
        val rootScore = publicProperty<Score>("root-score")

        const val ROOT_SCORE_NAME = "<root>"
    }
}