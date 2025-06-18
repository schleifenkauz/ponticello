package ponticello.ui.actions

import fxutils.Ctrl
import fxutils.Shift
import fxutils.actions.isTargetTextInput
import fxutils.modifiers
import fxutils.noModifiers
import hextant.context.Context
import hextant.context.compoundEdit
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.VerticalDirection.DOWN
import javafx.geometry.VerticalDirection.UP
import javafx.scene.Scene
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObject
import ponticello.ui.score.NavigableScorePane
import ponticello.ui.score.ScoreObjectSelectionManager
import ponticello.ui.score.ScoreObjectView

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
                val newInst = if (ev.isShiftDown) {
                    val name = context[ScoreObjectRegistry].nameForClone(inst.obj, ev) ?: return@addEventFilter
                    inst.clone(position, name)
                } else inst.duplicate(position)
                inst.score!!.addObject(newInst, autoSelect = true)
            } else if (!ev.isTargetTextInput) {
                val selected = selector.selectedViews
                    .associateBy { v -> v.instance }.values //filters out views that display the same instance
                val resizeMode = when (ev.modifiers) {
                    noModifiers -> null
                    setOf(Shift) -> ScoreObject.ResizeMode.Stretch
                    setOf(Ctrl, Shift) -> ScoreObject.ResizeMode.Regular
                    else -> return@addEventFilter
                }
                context.compoundEdit("Move objects") {
                    for (view in selected) {
                        when (ev.code) {
                            KeyCode.LEFT -> view.adjustHorizontal(direction = LEFT, resizeMode)
                            KeyCode.RIGHT -> view.adjustHorizontal(direction = RIGHT, resizeMode)
                            KeyCode.UP -> view.adjustVertical(direction = UP, resizeMode)
                            KeyCode.DOWN -> view.adjustVertical(direction = DOWN, resizeMode)
                            else -> throw AssertionError()
                        }
                    }
                }
            }
            ev.consume()
        }
    }

}