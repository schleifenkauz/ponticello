package xenakis.model

import hextant.core.editor.ListenerManager

class ObjectPosition(
    private var _time: Double = 0.0,
    private var _y: Double = 0.0
) : Comparable<ObjectPosition> {
    private val viewManager = ListenerManager.createWeakListenerManager<Listener>()

    var time: Double
        get() = _time
        set(value) {
            if (_time == value) return
            _time = value
            update()
        }

    var y: Double
        get() = _y
        set(value) {
            if (_y == value) return
            _y = value
            update()
        }

    private fun update() {
        viewManager.notifyListeners { moved(time, y) }
    }

    fun set(time: Double, y: Double) {
        _time = time
        _y = y
        viewManager.notifyListeners { moved(this@ObjectPosition.time, y) }
    }

    fun set(position: ObjectPosition) {
        set(position.time, position.y)
    }

    operator fun plus(position: ObjectPosition) = ObjectPosition(this.time + position.time, this.y + position.y)

    fun addListener(listener: Listener) {
        viewManager.addListener(listener)
    }

    fun removeListener(listener: Listener) {
        viewManager.removeListener(listener)
    }

    override fun toString(): String = "start: $time, y: $y"

    override fun compareTo(other: ObjectPosition): Int =
        compareValuesBy(this, other, ObjectPosition::time, ObjectPosition::y)

    fun copy(): ObjectPosition = ObjectPosition(time, y)

    interface Listener {
        fun moved(start: Double, y: Double)
    }

    companion object {
        fun origin() = ObjectPosition(0.0, 0.0)
    }
}