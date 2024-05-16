package xenakis.ui

import javafx.beans.property.SimpleBooleanProperty
import javafx.css.PseudoClass
import javafx.geometry.BoundingBox
import javafx.geometry.Bounds
import javafx.geometry.Insets
import javafx.scene.Cursor
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color.WHITE
import xenakis.impl.Point
import xenakis.impl.UDPSuperColliderClient
import xenakis.model.ScoreObject
import xenakis.model.XenakisProject
import kotlin.math.absoluteValue

abstract class ScoreObjectView(open val obj: ScoreObject, val project: XenakisProject) : VBox() {
    private var dragStart: Point? = null
    private var oldBounds: Bounds? = null
    private var draggedObject: ScoreObjectView = this

    protected lateinit var scoreView: ScoreView

    private val nameLabel = Label().styleClass("score-object-name")
    private lateinit var muteUnmuteBtn: Button
    protected val header = HBox(10.0)
    private val actions = HBox(10.0)
    protected val contents = VBox().styleClass("score-object-content")
    protected val envelopesPane = Pane()

    private val envelopeEditors = mutableListOf<EnvelopeEditor>()

    private val selectedProperty = SimpleBooleanProperty(false)

    protected open val canUserChangeHeight: Boolean get() = true
    protected open val canUserChangeWidth: Boolean get() = true

    init {
        styleClass("score-object")
        alwaysUpdateCursor()
        setupDragging()
        envelopesPane.widthProperty().addListener { _ -> rescale() }
        envelopesPane.heightProperty().addListener { _ -> rescale() }
    }

    fun init(parent: ScoreView) {
        this.scoreView = parent
        setupHeader()
        children.setAll(header, contents)
        header.prefWidthProperty().bind(this.widthProperty())
        contents.prefWidthProperty().bind(this.widthProperty())
        contents.prefHeightProperty().bind(this.heightProperty())
        repaint()
    }

    protected fun addAction(icon: Icon, action: String?, onAction: () -> Unit): Button {
        val button = icon.button(action = action)
        button.styleClass("score-object-btn")
        button.setOnAction { onAction() }
        actions.children.add(0, button)
        return button
    }

    open fun repaint() {
        layoutX = scoreView.getX(obj.start)
        layoutY = obj.y
        prefWidth = getDisplayWidth()
        prefHeight = obj.height
        padding = Insets(2.0)
        nameLabel.text = obj.name
        recolor()
        paintEnvelopes()
    }

    fun recolor() {
        if (obj.color != null) {
            contents.border =
                Border(BorderStroke(obj.color, BorderStrokeStyle.SOLID, CornerRadii(3.0), BorderWidths(2.0)))
        }
    }

    private fun paintEnvelopes() {
        for (editors in envelopeEditors) {
            editors.removeChildren()
        }
        if (obj.associatedEnvelopes.isEmpty()) return
        if (envelopesPane !in contents.children) contents.children.add(envelopesPane)
        setVgrow(envelopesPane, Priority.ALWAYS)
        for (control in obj.associatedEnvelopes) {
            if (!control.display) continue
            val parameter = control.parameter
            val envelope = control.envelope
            val e = EnvelopeEditor(
                parameterName = parameter, associatedObject = obj,
                points = envelope.points,
                pane = envelopesPane, scoreView = scoreView,
                contrastColor = WHITE, fixEdgePoints = true
            )
            e.repaint()
            envelopeEditors.add(e)
        }
    }

    protected open fun setupHeader() {
        header.styleClass("score-object-header")
        nameLabel.textFill = WHITE
        header.children.setAll(nameLabel, infiniteSpace(), actions)
        addAction(Icon.Delete, "Delete this object", ::delete)
        addAction(Icon.Play, "Play this object") {
            val client = project.context[UDPSuperColliderClient]
            project.prepareForPlay(client)
            obj.play(client)
        }
        muteUnmuteBtn = addAction(if (obj.muted) Icon.Mute else Icon.Unmute, "Toggle mute", ::toggleMute)
        header.visibleProperty().bind(hoverProperty().or(selectedProperty))
    }

    private fun toggleMute() {
        obj.muted = !obj.muted
        muteUnmuteBtn.graphic = if (obj.muted) Icon.Mute.getView() else Icon.Unmute.getView()
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
                resize(oldBounds!!, dx, dy, cursor, ev)
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
        x >= 0.0 && y >= 0.0 && x + width <= scoreView.width && y + height <= scoreView.height

    private fun relocateBy(old: Bounds, dx: Double, dy: Double) {
        val x = (old.minX + dx).snap(scoreView.timeSnap)
        val y = (old.minY + dy)
        if (!isInParentBounds(x, y, prefWidth, prefHeight)) return
        relocateObject(x, y)
    }

    private fun relocateObject(x: Double, y: Double) {
        scoreView.score.moveObject(obj, scoreView.getTime(x), y)
    }

    private fun updateCursor(x: Double, y: Double) {
        val tx = scaleX * 5
        val ty = scaleY * 5
        val dx = (x - prefWidth).absoluteValue
        val dy = (y - prefHeight).absoluteValue
        cursor = when {
            x.absoluteValue < tx && y.absoluteValue < ty && canUserChangeHeight && canUserChangeWidth -> Cursor.NW_RESIZE
            x.absoluteValue < tx && dy.absoluteValue < ty && canUserChangeHeight && canUserChangeWidth -> Cursor.SW_RESIZE
            dx < tx && y.absoluteValue < ty && canUserChangeHeight && canUserChangeWidth -> Cursor.NE_RESIZE
            dx < tx && dy < ty && canUserChangeHeight && canUserChangeWidth -> Cursor.SE_RESIZE
            x.absoluteValue < tx && canUserChangeWidth -> Cursor.W_RESIZE
            dx < tx && canUserChangeWidth -> Cursor.E_RESIZE
            y.absoluteValue < ty && canUserChangeHeight -> Cursor.N_RESIZE
            dy < ty && canUserChangeHeight -> Cursor.S_RESIZE
            else -> Cursor.DEFAULT
        }
    }

    private fun resize(
        x: Double, y: Double,
        width: Double, height: Double,
        ev: MouseEvent, resizeFromLeft: Boolean = false
    ) {
        val snappedX = x.snap(scoreView.timeSnap)
        val snappedWidth = width.snap(scoreView.timeSnap)
        if (snappedWidth < 10.0 || height < 10.0) return
        if (!isInParentBounds(snappedX, y, snappedWidth, height)) return
        setObjectWidth(snappedWidth, ev, resizeFromLeft)
        setPrefSize(scoreView.getWidth(obj.duration), height)
        obj.height = height
        relocateObject(snappedX, y)
    }

    protected open fun setObjectWidth(width: Double, ev: MouseEvent, resizeFromLeft: Boolean) {
        obj.duration = scoreView.getDuration(width)
    }

    open fun getDisplayWidth(): Double = scoreView.getWidth(obj.duration)

    protected open fun rescale() {
        rescaleEnvelopes()
    }

    private fun rescaleEnvelopes() {
        for (e in envelopeEditors) {
            e.repaint()
        }
    }

    private fun resize(old: Bounds, dx: Double, dy: Double, cursor: Cursor, ev: MouseEvent) {
        when (cursor) {
            Cursor.NW_RESIZE -> resize(old.minX + dx, old.minY + dy, old.width - dx, old.height - dy, ev, true)
            Cursor.N_RESIZE -> resize(old.minX, old.minY + dy, old.width, old.height - dy, ev)
            Cursor.NE_RESIZE -> resize(old.minX, old.minY + dy, old.width + dx, old.height - dy, ev)
            Cursor.E_RESIZE -> resize(old.minX, old.minY, old.width + dx, old.height, ev)
            Cursor.SE_RESIZE -> resize(old.minX, old.minY, old.width + dx, old.height + dy, ev)
            Cursor.S_RESIZE -> resize(old.minX, old.minY, old.width, old.height + dy, ev)
            Cursor.SW_RESIZE -> resize(old.minX + dx, old.minY + dy, old.width - dx, old.height + dy, ev, true)
            Cursor.W_RESIZE -> resize(old.minX + dx, old.minY, old.width - dx, old.height, ev, true)
        }
    }

    fun setSelected(value: Boolean) {
        pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), value)
        selectedProperty.set(value)
    }

    open fun onRemove() {

    }
}