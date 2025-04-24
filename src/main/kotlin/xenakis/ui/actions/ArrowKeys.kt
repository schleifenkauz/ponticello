package xenakis.ui.actions

import fxutils.actions.isTargetTextInput
import hextant.context.Context
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.VerticalDirection
import javafx.scene.Scene
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import xenakis.model.score.ObjectPosition
import xenakis.ui.impl.resizeMode
import xenakis.ui.score.NavigableScorePane
import xenakis.ui.score.ScoreObjectSelectionManager
import xenakis.ui.score.ScoreObjectView

object ArrowKeys {
    fun registerArrowKeys(scene: Scene, context: Context) {
        val selector = context[ScoreObjectSelectionManager]
        scene.addEventFilter(KeyEvent.KEY_PRESSED) { ev ->
            if (ev.code in setOf(KeyCode.PAGE_UP, KeyCode.PAGE_DOWN)) {
                val rootPane = selector.focusedScorePane.root
                if (rootPane is NavigableScorePane) {
                    val delta = if (ev.code == KeyCode.PAGE_DOWN) 100.0 else -100.0
                    rootPane.scroll(delta / rootPane.pixelsPerSecond)
                }
            }
            if (ev.isTargetTextInput) return@addEventFilter
            if (ev.target !is ScoreObjectView) return@addEventFilter
            if (ev.code !in setOf(KeyCode.LEFT, KeyCode.RIGHT, KeyCode.UP, KeyCode.DOWN)) return@addEventFilter
            if (ev.isAltDown) {
                val inst = selector.focusedInstance ?: return@addEventFilter
                if (ev.code !in setOf(KeyCode.LEFT, KeyCode.RIGHT)) return@addEventFilter
                val start = if (ev.code == KeyCode.RIGHT) inst.start + inst.obj.duration
                else inst.start - inst.obj.duration
                val position = ObjectPosition(start, inst.y)
                val newInst = if (ev.isShiftDown) inst.clone(position) else inst.duplicate(position)
                inst.score!!.addObject(newInst, autoSelect = true)
            } else if (!ev.isTargetTextInput) {
                val selected = selector.selectedViews
                    .associateBy { v -> v.instance }.values //filters out views that display the same instance
                val resize = ev.isControlDown
                val resizeType = ev.resizeMode ?: return@addEventFilter
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