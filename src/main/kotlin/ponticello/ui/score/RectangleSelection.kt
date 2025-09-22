package ponticello.ui.score

import fxutils.drag.setupDraggingAndResizing
import fxutils.runAfterLayout
import fxutils.styleClass
import javafx.geometry.Bounds
import javafx.scene.layout.Region
import ponticello.impl.abs
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectInstance

class RectangleSelection(val pane: ScorePane, initialPosition: ObjectPosition) {
    private val rect = Region() styleClass "selection-rect"

    private var corner1 = initialPosition
    private var corner2 = initialPosition

    val time get() = minOf(corner1.time, corner2.time)
    val y get() = minOf(corner1.y, corner2.y)
    val height get() = (corner1.y - corner2.y).abs()
    val duration get() = (corner1.time - corner2.time).abs()

    val bounds: Bounds get() = rect.boundsInParent

    init {
        rect.viewOrder = -1000.0
        setOppositeCorner(corner2)
        rect.setOnMouseClicked { _ -> rect.requestFocus() }
        rect.setupDraggingAndResizing(
            canUserChangeWidth = true, canUserChangeHeight = true,
            threshold = 12.0,
            drag = { x, y ->
                val dx = x - rect.layoutX
                val dy = y - rect.layoutY
                val deltaPos = ObjectPosition(pane.getDuration(dx), pane.getScoreY(dy))
                val newCorner1 = pane.snapToGrid(corner1 + deltaPos)
                corner2 += (newCorner1 - corner1)
                corner1 = newCorner1
                pane.markT(newCorner1.time)
                rescale()
            },
            resize = { _, _, rect ->
                var pos1 = pane.snapToGrid(rect.minX, rect.minY)
                var pos2 = pane.snapToGrid(rect.maxX, rect.maxY)
                if (corner1 < corner2 != pos1 < pos2) {
                    val tmp = pos1
                    pos1 = pos2
                    pos2 = tmp
                }
                if (pos1.time != corner1.time) pane.markT(pos1.time)
                else if (pos2.time != corner2.time) pane.markT(pos2.time)
                corner1 = pos1
                corner2 = pos2
                rescale()
            }
        )
    }

    fun setOppositeCorner(position: ObjectPosition) {
        corner2 = position
        rect.layoutX = pane.getX(time)
        rect.prefWidth = pane.getWidth(duration)
        val y1 = pane.getScreenY(corner1.y)
        val y2 = pane.getScreenY(corner2.y)
        rect.layoutY = minOf(y1, y2)
        rect.prefHeight = maxOf(y1, y2) - minOf(y1, y2)
    }

    fun createInstance(obj: ScoreObject): ScoreObjectInstance {
        obj.setInitialSize(duration, height)
        return ScoreObjectInstance(obj, time, y)
    }

    fun isEmpty(): Boolean = (rect.width * rect.height) < THRESHOLD

    fun rescale() {
        setOppositeCorner(corner2)
    }

    fun containedViews(mustBeContainedEntirely: Boolean): List<ScoreObjectView> {
        val pane = currentPane ?: return emptyList()
        return pane.children.filterIsInstance<ScoreObjectView>()
            .filter { v ->
                if (mustBeContainedEntirely) bounds.contains(v.boundsInParent)
                else bounds.intersects(v.boundsInParent)
            }
    }


    companion object {
        private const val THRESHOLD = 5.0

        private var instance: RectangleSelection? = null
        private var currentPane: ScorePane? = null

        fun get() = instance

        fun get(pane: ScorePane) = instance?.takeIf { currentPane == pane }

        fun clear() {
            val pane = currentPane ?: return
            val inst = instance ?: return
            pane.children.remove(inst.rect)
            currentPane = null
            instance = null
        }

        fun create(pane: ScorePane, position: ObjectPosition) {
            clear()
            val selection = RectangleSelection(pane, position)
            instance = selection
            currentPane = pane
            pane.children.add(instance!!.rect)
            runAfterLayout { selection.rect.requestFocus() }
        }

        fun reposition() {
            instance?.rescale()
        }
    }
}