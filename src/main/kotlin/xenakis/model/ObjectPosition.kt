package xenakis.model

import hextant.core.editor.ViewManager
import xenakis.ui.PositionListener

class ObjectPosition(
    private val obj: ScoreObject,
    private var _start: Double = 0.0,
    private var _y: Double = 0.0
) : Comparable<ObjectPosition> {
    private val viewManager = ViewManager.createWeakViewManager<PositionListener>()

    var start: Double
        get() = _start
        set(value) {
            if (_start == value) return
            _start = value
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
        viewManager.notifyViews { moved(obj, start, y) }
    }

    fun set(time: Double, y: Double) {
        _start = time
        _y = y
        viewManager.notifyViews { moved(obj, start, y) }
    }

    fun set(position: ObjectPosition) {
        set(position.start, position.y)
    }

    fun addListener(listener: PositionListener) {
        viewManager.addView(listener)
    }

    fun removeListener(listener: PositionListener) {
        viewManager.removeView(listener)
    }

    override fun toString(): String = "start: $start, y: $y"

    override fun compareTo(other: ObjectPosition): Int =
        compareValuesBy(this, other, ObjectPosition::start, ObjectPosition::y)

    fun copy(): ObjectPosition = ObjectPosition(obj, start, y)
}