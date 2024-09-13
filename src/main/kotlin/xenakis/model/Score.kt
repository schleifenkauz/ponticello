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
import java.util.logging.Logger

@Serializable
class Score(private val instances: MutableList<ScoreObjectInstance> = mutableListOf()) {
    val objectInstances: List<ScoreObjectInstance> get() = instances

    val objects = objectInstances.map { inst -> inst.obj }

    lateinit var scoreName: ReactiveString
        private set

    @Transient
    lateinit var context: Context

    @Transient
    private var initialized = false

    @Transient
    private val views = ListenerManager.createWeakListenerManager<ScoreListener>()

    private val undo by lazy { context[UndoManager] }

    fun initialize(context: Context, scoreName: ReactiveString) {
        if (initialized) return
        this.scoreName = scoreName
        this.context = context
        if (scoreName.now == ROOT_SCORE_NAME) context[rootScore] = this
        for (inst in objectInstances) {
            inst.initialize(context)
            inst.addToScore(this)
        }
    }

    fun addListener(listener: ScoreListener) {
        views.addListener(listener)
        for (inst in objectInstances) {
            listener.addedObject(inst)
        }
    }

    fun has(name: String) = objects.any { obj -> obj.name.now == name }

    fun getObject(name: String) =
        objects.find { obj -> obj.name.now == name }
            ?: error("ScoreObject $name not found in ${scoreName.now}")

    fun getSubScore(name: String): Score {
        val obj = getObject(name)
        val group = obj as? ScoreObjectGroup ?: error("$obj is not a sub score")
        return group.score
    }

    fun deepClone() = Score(instances.mapTo(mutableListOf()) { inst ->
        val name = context[ScoreObjectRegistry].nameForClone(inst.obj)
        inst.clone(name, inst.position)
    })

    fun clone() = Score(instances.mapTo(mutableListOf()) { inst -> inst.duplicate(inst.position) })

    fun addObject(inst: ScoreObjectInstance) {
        inst.initialize(context)
        logger.info("Adding object ${inst.obj.name.now} ${inst.position}")
        inst.addToScore(this)
        instances.add(inst)
        views.notifyListeners { addedObject(inst) }
        undo.record(ScoreEdit.AddObject(inst, this))
    }

    fun removeObjects(set: Set<ScoreObjectInstance>) {
        for (inst in set) {
            logger.info("Removing ${inst.obj.name.now}")
            instances.remove(inst)
            views.notifyListeners { removedObject(inst) }
        }
        undo.record(ScoreEdit.RemoveObjects(set, this))
    }

    fun removeObject(obj: ScoreObjectInstance) {
        removeObjects(setOf(obj))
    }

    fun addTime(location: Double, amount: Double) {
        undo.record(ScoreEdit.AddTime(location, amount, this))
        context.withoutUndo {
            for (inst in objectInstances) {
                if (inst.time > location) {
                    val newStart = inst.time + amount
                    inst.setTime(newStart)
                }
            }
        }
    }

    fun deleteTimeRange(start: Double, end: Double) {
        undo.beginCompoundEdit()
        val removedDuration = end - start
        for (inst in objectInstances) {
            if (inst.time > start) {
                if (inst.time + inst.obj.duration < end) {
                    removeObject(inst)
                } else {
                    val newStart = (inst.time - removedDuration).coerceAtLeast(0.0)
                    inst.setTime(newStart)
                }
            }
        }
        undo.finishCompoundEdit("Delete time range")
    }

    fun loop(inst: ScoreObjectInstance, period: Double, repetitions: Int) {
        context[UndoManager].beginCompoundEdit("Loop object")
        var t = inst.time
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

    fun hasInstancesOf(obj: ScoreObject): Boolean = instancesOf(obj).any()

    fun instancesOf(obj: ScoreObject): Sequence<ScoreObjectInstance> = sequence {
        for (inst in objectInstances) {
            val o = inst.obj
            if (o == obj) yield(inst)
            else if (o is ScoreObjectGroup) yieldAll(o.score.instancesOf(obj))
        }
    }

    companion object {
        val rootScore = publicProperty<Score>("root-score")

        const val ROOT_SCORE_NAME = "<root>"

        private val logger = Logger.getLogger("Score")
    }
}