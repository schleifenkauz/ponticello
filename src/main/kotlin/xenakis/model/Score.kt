package xenakis.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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
}