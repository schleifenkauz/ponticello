package ponticello.ui.actions

import fxutils.Ctrl
import fxutils.Shift
import fxutils.actions.isTargetTextInput
import fxutils.modifiers
import fxutils.noModifiers
import fxutils.prompt.atMouseCoords
import fxutils.undo.AbstractEdit
import fxutils.undo.Edit
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.context.withoutUndo
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.Side
import javafx.geometry.VerticalDirection.DOWN
import javafx.geometry.VerticalDirection.UP
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.layout.Region
import ponticello.impl.Decimal
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObject.ResizeMode.Regular
import ponticello.model.score.ScoreObject.ResizeMode.Stretch
import ponticello.model.score.ScoreObjectInstance
import ponticello.ui.score.NavigableScorePane
import ponticello.ui.score.ScoreObjectSelectionManager
import ponticello.ui.score.ScoreObjectView

object ArrowKeys {
    fun registerArrowKeys(region: Region, context: Context) {
        val selector = context[ScoreObjectSelectionManager]
        region.addEventFilter(KeyEvent.KEY_PRESSED) { ev ->
            if (ev.code in setOf(KeyCode.PAGE_UP, KeyCode.PAGE_DOWN)) {
                val rootPane = selector.focusedScorePane.root
                if (rootPane is NavigableScorePane) {
                    val delta = if (ev.code == KeyCode.PAGE_DOWN) 100.0 else -100.0
                    rootPane.scroll(delta / rootPane.pixelsPerSecond)
                }
            }
            if (ev.isTargetTextInput) return@addEventFilter
            if (ev.code !in setOf(KeyCode.LEFT, KeyCode.RIGHT, KeyCode.UP, KeyCode.DOWN)) return@addEventFilter
            if (ev.isAltDown) {
                val inst = selector.focusedInstance ?: return@addEventFilter
                if (ev.code !in setOf(KeyCode.LEFT, KeyCode.RIGHT)) return@addEventFilter
                val start = if (ev.code == KeyCode.RIGHT) inst.start + inst.obj.duration
                else inst.start - inst.obj.duration
                val position = ObjectPosition(start, inst.y)
                val newInst = if (ev.isShiftDown) {
                    val placement = ev.atMouseCoords()
                    val name = context[ScoreObjectRegistry].nameForClone(inst.obj, placement) ?: return@addEventFilter
                    inst.clone(position, name)
                } else inst.duplicate(position)
                inst.score!!.addObject(newInst, autoSelect = true)
            } else if (!ev.isTargetTextInput) {
                val selected = selector.selectedViews
                    .associateBy { v -> v.instance }.values //filters out views that display the same instance
                val resizeMode = when (ev.modifiers) { //TODO think about which modifiers to use
                    noModifiers -> null
                    setOf(Shift) -> Stretch
                    setOf(Ctrl, Shift) -> Regular
                    else -> return@addEventFilter
                }
                val positionsBefore = selected.map { it.instance.position }
                val durationsBeforeResize = selected.map { it.instance.obj.duration }
                val heightsBeforeResize = selected.map { it.instance.obj.height }
                context.withoutUndo {
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
                if (resizeMode == null) {
                    val positionsAfter = selected.map { it.instance.position }
                    context[UndoManager].record(
                        ArrowMoveEdit(
                            selected.map(ScoreObjectView::instance),
                            positionsBefore, positionsAfter
                        )
                    )
                } else {
                    val durationsAfterResize = selected.map { it.obj.duration }
                    val heightsAfterResize = selected.map { it.obj.height }
                    context[UndoManager].record(
                        ArrowResizeEdit(
                            selected.map(ScoreObjectView::instance),
                            resizeMode,
                            durationsBeforeResize, durationsAfterResize,
                            heightsBeforeResize, heightsAfterResize
                        )
                    )
                }

            }
            ev.consume()
        }
    }

    private class ArrowMoveEdit(
        private val objects: List<ScoreObjectInstance>,
        private val positionsBeforeMove: List<ObjectPosition>,
        private val positionsAfterMove: List<ObjectPosition>,
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Move objects"

        override fun doRedo() {
            objects.zip(positionsAfterMove) { obj, pos -> obj.moveTo(pos) }
        }

        override fun doUndo() {
            objects.zip(positionsBeforeMove) { obj, pos -> obj.moveTo(pos) }
        }

        override fun mergeWith(other: Edit): Edit? = when {
            other !is ArrowMoveEdit -> null
            other.objects != this.objects -> null
            else -> ArrowMoveEdit(objects, positionsBeforeMove, other.positionsAfterMove)
        }
    }

    private class ArrowResizeEdit(
        private val objects: List<ScoreObjectInstance>,
        private val resizeMode: ScoreObject.ResizeMode,
        private val durationsBeforeResize: List<Decimal>,
        private val durationsAfterResize: List<Decimal>,
        private val heightsBeforeResize: List<Decimal>,
        private val heightsAfterResize: List<Decimal>,
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Resize " + if (objects.size > 1) "objects" else "object"

        override fun doRedo() {
            restoreState(durationsAfterResize, heightsAfterResize)
        }

        override fun doUndo() {
            restoreState(durationsBeforeResize, heightsBeforeResize)
        }

        private fun restoreState(durations: List<Decimal>, heights: List<Decimal>) {
            for ((idx, inst) in objects.withIndex()) {
                inst.obj.resize(durations[idx], inst.obj.height, resizeMode, Side.RIGHT)
                inst.obj.resize(inst.obj.duration, heights[idx], resizeMode, Side.BOTTOM)
            }
        }
    }
}