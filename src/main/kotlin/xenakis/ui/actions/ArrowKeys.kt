package xenakis.ui.actions

import fxutils.actions.isTargetTextInput
import fxutils.runFXWithTimeout
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.VerticalDirection
import javafx.scene.Scene
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import reaktive.value.now
import xenakis.model.score.ObjectPosition
import xenakis.ui.impl.resizeType
import xenakis.ui.launcher.XenakisMainActivity
import xenakis.ui.score.ScoreObjectSelectionManager
import xenakis.ui.score.ScoreObjectView

object ArrowKeys {
    fun registerArrowKeys(scene: Scene, activity: XenakisMainActivity) {
        val scoreView = activity.scoreView
        val context = activity.context
        scene.addEventFilter(KeyEvent.KEY_PRESSED) { ev ->
            if (ev.isTargetTextInput) return@addEventFilter
            if (ev.code in setOf(KeyCode.PAGE_UP, KeyCode.PAGE_DOWN)) {
                val delta = if (ev.code == KeyCode.PAGE_DOWN) 100.0 else -100.0
                scoreView.scroll(delta / scoreView.pixelsPerSecond)
            }
            if (ev.target !is ScoreObjectView) return@addEventFilter
            if (ev.code !in setOf(KeyCode.LEFT, KeyCode.RIGHT, KeyCode.UP, KeyCode.DOWN)) return@addEventFilter
            if (ev.isAltDown) {
                val view = context[ScoreObjectSelectionManager].focusedView.now ?: return@addEventFilter
                val inst = view.instance
                if (ev.code !in setOf(KeyCode.LEFT, KeyCode.RIGHT)) return@addEventFilter
                val start = if (ev.code == KeyCode.RIGHT) inst.start + inst.obj.duration
                else inst.start - inst.obj.duration
                val position = ObjectPosition(start, inst.y)
                val newInst = if (ev.isShiftDown) inst.clone(position) else inst.duplicate(position)
                inst.score!!.addObject(newInst)
                val newView = view.pane.getObjectView(newInst)
                runFXWithTimeout(10) {
                    context[ScoreObjectSelectionManager].select(newView, addToSelection = false)
                }
            } else {
                val selected = context[ScoreObjectSelectionManager].selectedViews
                    .associateBy { v -> v.instance }.values //filters out views that display the same instance
                val resize = ev.isControlDown
                val resizeType = ev.resizeType ?: return@addEventFilter
                for (view in selected) {
                    when (ev.code) {
                        KeyCode.LEFT -> view.adjustHorizontal(direction = HorizontalDirection.LEFT, resize, resizeType)
                        KeyCode.RIGHT -> view.adjustHorizontal(direction = RIGHT, resize, resizeType)
                        KeyCode.UP -> view.adjustVertical(direction = VerticalDirection.UP, resize, resizeType)
                        KeyCode.DOWN -> view.adjustVertical(direction = VerticalDirection.DOWN, resize, resizeType)
                        else -> throw AssertionError()
                    }
                }
            }
            ev.consume()
        }
    }

}