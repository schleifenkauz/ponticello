package ponticello.model.score

import bundles.publicProperty
import bundles.set
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.context.compoundEdit
import hextant.context.withoutUndo
import hextant.core.editor.ListenerManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveString
import reaktive.value.now
import reaktive.value.reactiveValue
import ponticello.impl.Decimal
import ponticello.impl.Logger
import ponticello.impl.one
import ponticello.impl.zero
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.registry.ScoreObjectRegistry

@Serializable
class Score(
    private val instances: MutableList<ScoreObjectInstance> = mutableListOf(),
) : AbstractContextualObject() {
    val objectInstances: List<ScoreObjectInstance> get() = instances

    val objects get() = objectInstances.mapTo(mutableSetOf()) { inst -> inst.obj }

    @Transient
    var scoreName: ReactiveString = reactiveValue(ROOT_SCORE_NAME)
        private set
    @Transient
    var maxTime = reactiveValue(Decimal.INF)
        private set
    @Transient
    var maxY = reactiveValue(one)
        private set

    @Transient
    private val views = ListenerManager.createWeakListenerManager<ScoreListener>()

    private val undo get() = context[UndoManager]

    override fun initialize(context: Context) {
        super.initialize(context)
        context[rootScore] = this
        for (inst in objectInstances) {
            inst.initialize(context)
            inst.addedToScore(this)
        }
    }

    fun initialize(context: Context, parentObject: ScoreObject) {
        initialize(context)
        scoreName = parentObject.name
        maxTime = parentObject.duration()
        maxY = parentObject.height()
    }

    fun addListener(listener: ScoreListener, notify: Boolean = true) {
        views.addListener(listener)
        if (notify) {
            for (inst in objectInstances) {
                listener.addedObject(this, inst, autoSelect = false)
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

    fun addObject(inst: ScoreObjectInstance, autoSelect: Boolean) {
        inst.initialize(context)
        inst.addedToScore(this)
        Logger.info("Adding object ${inst.obj.name.now} at ${inst.position} to ${scoreName.now}", Logger.Category.Score)
        instances.add(inst)
        views.notifyListeners { addedObject(this@Score, inst, autoSelect) }
        undo.record(ScoreEdit.AddObject(inst, this))
    }

    fun addObject(obj: ScoreObject, time: Decimal, y: Decimal, autoSelect: Boolean) {
        val inst = ScoreObjectInstance(obj, time, y)
        addObject(inst, autoSelect)
    }

    fun removeObjects(set: Set<ScoreObjectInstance>, option: RegistryOption) {
        for (inst in set) {
            Logger.info("Removing ${inst.obj.name.now} from score ${scoreName.now}", Logger.Category.Score)
            instances.remove(inst)
            views.notifyListeners { removedObject(this@Score, inst) }
            inst.removedFromScore(option)
        }
        undo.record(ScoreEdit.RemoveObjects(set, option, this))
    }

    fun removeObject(obj: ScoreObjectInstance, option: RegistryOption) {
        removeObjects(setOf(obj), option)
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
        context.compoundEdit("Delete time range") {
            val removedDuration = end - start
            for (inst in objectInstances) {
                if (inst.start > start) {
                    if (inst.start + inst.obj.duration < end) {
                        removeObject(inst, option = RegistryOption.REMOVE_WITHOUT_ASKING)
                    } else {
                        val newStart = (inst.start - removedDuration).coerceAtLeast(zero)
                        inst.setTime(newStart)
                    }
                }
            }
        }
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

    enum class RegistryOption {
        KEEP_IN_REGISTRY,
        REMOVE_WITHOUT_ASKING,
        ASK_IF_NEEDED;
    }

    companion object {
        val rootScore = publicProperty<Score>("root-score")

        const val ROOT_SCORE_NAME = "<root>"
    }
}