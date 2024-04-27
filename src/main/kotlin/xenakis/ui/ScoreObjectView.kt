package xenakis.ui

import javafx.css.PseudoClass
import javafx.geometry.BoundingBox
import javafx.geometry.Bounds
import javafx.geometry.Insets
import javafx.scene.Cursor
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color.*
import xenakis.impl.Point
import xenakis.impl.UDPSuperColliderClient
import xenakis.model.ScoreObject
import xenakis.model.XenakisProject
import xenakis.ui.ScoreView.Companion.PIXELS_PER_SECOND
import kotlin.math.absoluteValue

abstract class ScoreObjectView(open val obj: ScoreObject, val project: XenakisProject) : VBox() {
    private var dragStart: Point? = null
    private var oldBounds: Bounds? = null
    private var draggedObject: ScoreObjectView = this

    lateinit var scoreView: ScoreView
        private set

    private val header = HBox(10.0)
    private val envelopesPane = Pane()

    private val envelopeEditors = mutableListOf<EnvelopeEditor>()

    init {
        styleClass.add("score-object")
        alwaysUpdateCursor()
        setupDragging()
        envelopesPane.widthProperty().addListener { _ -> rescaleEnvelopes() }
        envelopesPane.heightProperty().addListener { _ -> rescaleEnvelopes() }
    }

    protected fun addFunction(icon: Icon, action: String?, onAction: () -> Unit) {
        val button = icon.button(24.0, action)
        button.styleClass("score-object-btn")
        button.setOnAction { onAction() }
        header.children.add(2, button)
        button.antiScale(scoreView)
    }

    open fun repaint() {
        children.clear()
        layoutX = obj.start * PIXELS_PER_SECOND
        layoutY = obj.y
        prefWidth = obj.duration * PIXELS_PER_SECOND
        prefHeight = obj.height
        padding = Insets(2.0)
        border = Border(BorderStroke(obj.color, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, BorderWidths(2.0)))
        setupHeader()
        paintEnvelopes()
    }

    private fun paintEnvelopes() {
        envelopeEditors.clear()
        envelopesPane.children.clear()
        if (obj.associatedEnvelopes.isEmpty()) return
        children.add(envelopesPane)
        setVgrow(envelopesPane, Priority.ALWAYS)
        for (control in obj.associatedEnvelopes) {
            if (!control.display) continue
            val parameter = control.parameter
            val envelope = control.envelope
            val e = EnvelopeEditor(
                parameterName = parameter, spec = control.spec,
                points = envelope.points,
                pane = envelopesPane, scoreView = scoreView,
                color = control.displayColor, contrastColor = WHITE,
                valueGrid = control.spec.step, fixEdgePoints = true
            )
            e.repaint()
            envelopeEditors.add(e)
        }
    }

    private fun setupHeader() {
        header.styleClass("score-object-header")
        val label = Label(obj.name).styleClass("score-object-name")
        label.textFill = WHITE
        val space = Region()
        HBox.setHgrow(space, Priority.ALWAYS);
        header.children.setAll(label, space)
        addFunction(Icon.Delete, "Delete this object", ::delete)
        addFunction(Icon.Play, "Play this object") {
            obj.play(project.context[UDPSuperColliderClient])
        }
        children.add(header)
    }

    private fun delete() {
        scoreView.score.removeObject(obj)
    }

    private fun alwaysUpdateCursor() {
        setOnMouseEntered { ev ->
            if (!ev.isPrimaryButtonDown) {
                updateCursor(ev.x, ev.y)
            }
        }
        setOnMouseMoved { ev ->
            if (!ev.isPrimaryButtonDown) {
                updateCursor(ev.x, ev.y)
            }
        }
        setOnMouseExited { ev ->
            if (!ev.isPrimaryButtonDown) cursor = Cursor.DEFAULT
        }
    }

    private fun setupDragging() {
        header.addEventHandler(MouseEvent.MOUSE_PRESSED) { ev ->
            if (isResizeCursor(cursor)) return@addEventHandler
            oldBounds = BoundingBox(layoutX, layoutY, prefWidth, prefHeight)
            dragStart = Point(ev.screenX, ev.screenY)
            draggedObject = if (ev.isShiftDown) {
                val clone = obj.clone(scoreView.score.nameForClone(obj))
                clone.initialize(project)
                scoreView.score.addObject(clone)
                scoreView.getObjectView(clone)
            } else this
            ev.consume()
        }
        addEventHandler(MouseEvent.MOUSE_PRESSED) { ev ->
            dragStart = Point(ev.screenX, ev.screenY)
            oldBounds = BoundingBox(layoutX, layoutY, prefWidth, prefHeight)
            ev.consume()
        }
        addEventHandler(MouseEvent.MOUSE_DRAGGED) { ev ->
            val start = dragStart ?: return@addEventHandler
            val dx = ((ev.screenX - start.x) / scoreView.scaleX)
            val dy = ((ev.screenY - start.y) / scoreView.scaleY)
            if (isResizeCursor(cursor)) {
                resize(oldBounds!!, dx, dy, cursor)
            } else {
                draggedObject.relocateBy(oldBounds!!, dx, dy)
            }
            ev.consume()
        }
        addEventHandler(MouseEvent.MOUSE_RELEASED) { ev ->
            if (draggedObject != this && draggedObject.boundsInParent == this.boundsInParent) {
                scoreView.score.removeObject(draggedObject.obj)
            }
            dragStart = null
            oldBounds = null
            draggedObject = this
            ev.consume()
        }
    }

    private fun isResizeCursor(cursor: Cursor) = cursor.toString().endsWith("RESIZE")

    private fun isInParentBounds(x: Double, y: Double, width: Double, height: Double) =
        x >= 0.0 && y >= 0.0 && x + width <= scoreView.prefWidth && y + height <= scoreView.prefHeight

    private fun relocateBy(old: Bounds, dx: Double, dy: Double) {
        val x = (old.minX + dx).snap(scoreView.timeSnap)
        val y = (old.minY + dy).snap(scoreView.timeSnap)
        if (!isInParentBounds(x, y, prefWidth, prefHeight)) return
        relocateObject(x, y)
    }

    private fun relocateObject(x: Double, y: Double) {
        relocate(x, y)
        obj.start = x / PIXELS_PER_SECOND
        obj.y = y
    }

    private fun updateCursor(x: Double, y: Double) {
        val tx = scaleX * 5
        val ty = scaleY * 5
        val dx = (x - prefWidth).absoluteValue
        val dy = (y - prefHeight).absoluteValue
        cursor = when {
            x.absoluteValue < tx && y.absoluteValue < ty -> Cursor.NW_RESIZE
            x.absoluteValue < tx && dy.absoluteValue < ty -> Cursor.SW_RESIZE
            dx < tx && y.absoluteValue < ty -> Cursor.NE_RESIZE
            dx < tx && dy < ty -> Cursor.SE_RESIZE
            x.absoluteValue < tx -> Cursor.W_RESIZE
            dx < tx -> Cursor.E_RESIZE
            y.absoluteValue < ty -> Cursor.N_RESIZE
            dy < ty -> Cursor.S_RESIZE
            else -> Cursor.DEFAULT
        }
    }

    private fun resize(x: Double, y: Double, width: Double, height: Double) {
        val snappedX = x.snap(scoreView.timeSnap)
        val snappedWidth = width.snap(scoreView.timeSnap)
        if (snappedWidth < 10.0 || height < 10.0) return
        if (!isInParentBounds(snappedX, y, snappedWidth, height)) return
        relocateObject(snappedX, y)
        resizeObject(snappedWidth, height)
    }

    private fun resizeObject(width: Double, height: Double) {
        setPrefSize(width, height)
        obj.duration = width / PIXELS_PER_SECOND
        obj.height = height
    }

    private fun rescaleEnvelopes() {
        for (e in envelopeEditors) {
            e.repaint()
        }
    }

    private fun resize(old: Bounds, dx: Double, dy: Double, cursor: Cursor) {
        when (cursor) {
            Cursor.NW_RESIZE -> resize(old.minX + dx, old.minY + dy, old.width - dx, old.height - dy)
            Cursor.N_RESIZE -> resize(old.minX, old.minY + dy, old.width, old.height - dy)
            Cursor.NE_RESIZE -> resize(old.minX, old.minY + dy, old.width + dx, old.height - dy)
            Cursor.E_RESIZE -> resize(old.minX, old.minY, old.width + dx, old.height)
            Cursor.SE_RESIZE -> resize(old.minX, old.minY, old.width + dx, old.height + dy)
            Cursor.S_RESIZE -> resize(old.minX, old.minY, old.width, old.height + dy)
            Cursor.SW_RESIZE -> resize(old.minX + dx, old.minY + dy, old.width - dx, old.height + dy)
            Cursor.W_RESIZE -> resize(old.minX + dx, old.minY, old.width - dx, old.height)
        }
    }

    fun setSelected(value: Boolean) {
        pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), value)
    }

    open fun onAddToScoreView(scoreView: ScoreView) {
        this.scoreView = scoreView
        repaint()
    }

    open fun onRemove() {

    }
}