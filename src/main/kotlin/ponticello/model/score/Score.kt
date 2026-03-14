package ponticello.model.score

import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.context.compoundEdit
import hextant.context.withoutUndo
import hextant.core.editor.ListenerManager
import javafx.geometry.Side
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.instr.MidiInstrument
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import reaktive.value.ReactiveString
import reaktive.value.now
import reaktive.value.reactiveValue

@Serializable
open class Score(
    private val instances: MutableList<ScoreObjectInstance> = mutableListOf(),
) : AbstractContextualObject(), ScoreObject.Listener {
    val objectInstances: List<ScoreObjectInstance> get() = instances

    @Transient
    private val instancesByObject = mutableMapOf<ScoreObject, MutableSet<ScoreObjectInstance>>()

    val objects: Set<ScoreObject>
        get() =
            if (initialized) instancesByObject.keys
            else instances.mapTo(mutableSetOf()) { it.obj }

    @Transient
    var parentObject: ScoreObject? = null
        private set

    val scoreName: ReactiveString get() = parentObject?.name ?: reactiveValue(ROOT_SCORE_NAME)

    val maxTime get() = parentObject?.duration ?: Decimal.INF
    val minY get() = parentObject?.minY ?: zero(precision = 4)
    val maxY get() = parentObject?.maxY ?: one(precision = 4)

    @Transient
    private val views = ListenerManager.createWeakListenerManager<ScoreListener>()

    @Transient
    private val intervalTree = IntervalTree<ScoreObjectInstance>()

    @Transient
    var isAuxiliary = false
        private set

    private val undo get() = context[UndoManager]

    override fun initialize(context: Context) {
        super.initialize(context)
        val itr = instances.listIterator()
        for (inst in itr) {
            inst.resolveObject(context)
            val obj = inst.obj
            if (parentObject is MidiObject && obj is SoundProcess) {
                obj.instrumentRef.set(MidiInstrument.reference())
            }
            inst.initialize(context)
            inst.addedToScore(this)
            if (inst.obj.duration <= zero && (inst.obj !is MemoObject && inst.obj !is TaskObject)) {
                Logger.warn("Removing zero-duration object $inst from score", Logger.Category.Score)
                itr.remove()
                continue
            }
            val instances = instancesByObject.getOrPut(inst.obj) { mutableSetOf() }
            instances.add(inst)
            if (instances.size == 1) {
                inst.obj.addListener(this)
            }
            intervalTree.add(inst, inst.start, inst.end)
        }
    }

    fun initialize(context: Context, parentObject: ScoreObject, auxiliary: Boolean = false) {
        this.parentObject = parentObject
        this.isAuxiliary = auxiliary
        initialize(context)
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

    fun deepClone(): Score {
        val clonedObjects = objects.associateWith { obj ->
            val newName = context[ScoreObjectRegistry].nameForClone(obj)
            obj.deepClone(newName)
        }
        val instances = instances.mapTo(mutableListOf()) { inst ->
            val obj = clonedObjects.getValue(inst.obj)
            ScoreObjectInstance(obj, inst.position, inst.muted.copy())
        }
        return Score(instances)
    }

    fun clone() = Score(instances.mapTo(mutableListOf()) { inst -> inst.duplicate() })

    fun addObject(inst: ScoreObjectInstance, autoSelect: Boolean) {
        if (inst.obj.duration <= zero && (inst.obj !is MemoObject && inst.obj !is TaskObject)) {
            Logger.error("Attempt to add zero-duration object $inst to the score")
            return
        }
        if (inst.obj == parentObject) {
            Logger.error("Cannot add ${inst.obj} as a child to itself", Logger.Category.Score)
        }
        inst.initialize(context)
        inst.addedToScore(this)
        Logger.info("Adding object ${inst.obj.name.now} at ${inst.position} to ${scoreName.now}", Logger.Category.Score)
        instances.add(inst)
        intervalTree.add(inst, inst.start, inst.end)
        val instances = instancesByObject.getOrPut(inst.obj) { mutableSetOf() }
        instances.add(inst)
        if (instances.size == 1) {
            inst.obj.addListener(this)
        }
        views.notifyListeners { addedObject(this@Score, inst, autoSelect) }
        undo.record(ScoreEdit.AddObject(inst, this))
    }

    fun addObject(obj: ScoreObject, time: Decimal, y: Decimal, autoSelect: Boolean): ScoreObjectInstance {
        val inst = ScoreObjectInstance(obj, time, y)
        addObject(inst, autoSelect)
        return inst
    }

    fun removeObjects(set: Set<ScoreObjectInstance>, option: RegistryOption) {
        for (inst in set) {
            Logger.info("Removing ${inst.obj.name.now} from score ${scoreName.now}", Logger.Category.Score)
            instances.remove(inst)
            intervalTree.remove(inst, inst.start, inst.end)
            instancesByObject[inst.obj]?.remove(inst)
            if (instancesByObject[inst.obj]?.isEmpty() == true) {
                instancesByObject.remove(inst.obj)
                inst.obj.removeListener(this)
            }
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
        val oldStart = oldPosition.time
        val oldEnd = oldPosition.time + inst.obj.duration
        reinsertInterval(inst, oldStart, oldEnd)
        views.notifyListeners { movedObject(this@Score, inst, dt, dy) }
    }

    override fun finishedResize(obj: ScoreObject, deltaDuration: Decimal, deltaHeight: Decimal, side: Side) {
        if (this.isAuxiliary) return
        if (side !in setOf(Side.LEFT, Side.RIGHT)) return
        for (inst in instancesByObject[obj].orEmpty()) {
            val oldStart = if (side == Side.LEFT) inst.start + deltaDuration else inst.start
            val oldEnd = if (side == Side.LEFT) inst.end else inst.end - deltaDuration
            reinsertInterval(inst, oldStart, oldEnd)
        }
    }

    private fun reinsertInterval(inst: ScoreObjectInstance, oldStart: Decimal, oldEnd: Decimal) {
        val removed = intervalTree.remove(inst, oldStart, oldEnd)
        if (!removed) {
            Logger.warn(
                "Failed to remove interval for ${inst.obj.name.now} from $oldStart to $oldEnd",
                Logger.Category.Score
            )
        }
        intervalTree.add(inst, inst.start, inst.end)
    }

    fun recomputeIntervals() {
        intervalTree.clear()
        for (inst in instances) {
            intervalTree.add(inst, inst.start, inst.end)
        }
        for (obj in objects) {
            if (obj is AbstractScoreObjectGroup) {
                obj.score.recomputeIntervals()
            }
        }
    }

    fun activeInstances(range: DecimalRange): List<ScoreObjectInstance> =
        intervalTree.queryOverlapping(range.start, range.endInclusive).map { it.value }

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
            val obj = parentObject
            obj?.resize(obj.duration + amount, obj.height, ScoreObject.ResizeMode.Regular, Side.RIGHT)
        }
    }

    fun deleteTimeRange(start: Decimal, end: Decimal) {
        context.compoundEdit("Delete time range") {
            val removedDuration = end - start
            for (inst in objectInstances.toList()) {
                if (inst.start > start) {
                    if (inst.start + inst.obj.duration < end) {
                        removeObject(inst, option = RegistryOption.REMOVE_WITHOUT_ASKING)
                    } else {
                        val newStart = (inst.start - removedDuration).coerceAtLeast(zero)
                        inst.setTime(newStart)
                    }
                }
            }
            val obj = parentObject
            obj?.resize(obj.duration - removedDuration, obj.height, ScoreObject.ResizeMode.Regular, Side.RIGHT)
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
        const val ROOT_SCORE_NAME = "<root>"

        fun makeAuxiliaryScore(obj: ScoreObject, context: Context) = when (obj) {
            is ScoreObjectGroup -> obj.score
            else -> {
                val inst = ScoreObjectInstance(obj, ObjectPosition.ZERO)
                val score = Score(mutableListOf(inst))
                score.initialize(context, parentObject = obj, auxiliary = true)
                score
            }
        }
    }
}