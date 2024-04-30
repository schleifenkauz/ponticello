package xenakis.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import xenakis.impl.ScWriter
import xenakis.ui.format

@Serializable
data class Score(
    private var _totalDuration: Double,
    private val _objects: MutableSet<ScoreObject> = mutableSetOf()
) {
    val objects: Set<ScoreObject> get() = _objects

    @Transient
    private val listeners = mutableListOf<ScoreListener>()
    var totalDuration: Double
        get() = _totalDuration
        set(value) {
            _totalDuration = value
            listeners.forEach { l -> l.setTotalDuration(value) }
        }

    fun addListener(listener: ScoreListener) {
        listeners.add(listener)
    }

    fun addObject(obj: ScoreObject) {
        _objects.add(obj)
        listeners.forEach { l -> l.addedObject(obj) }
    }

    fun removeObject(obj: ScoreObject) {
        _objects.remove(obj)
        listeners.forEach { l -> l.removedObject(obj) }
    }

    fun clear() {
        for (obj in objects) {
            listeners.forEach { l -> l.removedObject(obj) }
        }
        _objects.clear()
    }

    fun initialize(project: XenakisProject) {
        for (obj in objects) {
            obj.initialize(project)
        }
    }

    fun copyFrom(score: Score) {
        clear()
        totalDuration = score.totalDuration
        for (obj in score.objects) {
            addObject(obj)
        }
    }

    fun nameForClone(obj: ScoreObject): String = "${obj.name}_copy"

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