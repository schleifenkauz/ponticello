package xenakis.ui

import hextant.context.Context
import hextant.undo.UndoManager
import javafx.beans.property.SimpleBooleanProperty
import javafx.css.PseudoClass
import javafx.geometry.BoundingBox
import javafx.geometry.Bounds
import javafx.scene.Cursor
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.scene.paint.Color.BLACK
import javafx.scene.paint.Color.WHITE
import xenakis.impl.Point
import xenakis.impl.UDPSuperColliderClient
import xenakis.model.ScoreObject
import xenakis.ui.XenakisController.Companion.currentProject
import kotlin.math.absoluteValue

abstract class ScoreObjectView : VBox() {
    private var dragStart: Point? = null
    private var oldBounds: Bounds? = null
    private var draggedObject: ScoreObjectView = this

    protected lateinit var scoreView: ScoreView
    protected lateinit var context: Context

    private val nameLabel = Label().styleClass("score-object-name")
    private lateinit var muteUnmuteBtn: Button
    protected val header = HBox(10.0).centerChildrenVertically()
    protected val actions = HBox(10.0).centerChildrenVertically()
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
        children.setAll(header, contents)
        contents.alwaysVGrow()
    }

    abstract val obj: ScoreObject

    protected open val supportedActions get() = listOf(Icon.Delete, Icon.Play, Icon.Mute)

    private fun setupHeader() {
        header.styleClass("score-object-header")
        nameLabel.textFill = WHITE
        header.children.setAll(nameLabel, infiniteSpace(), actions)
        if (Icon.Mute in supportedActions) {
            val icon = if (obj.muted) Icon.Mute else Icon.Unmute
            muteUnmuteBtn = addAction(icon, "Toggle mute") {
                obj.muted = !obj.muted
            }
        }
        if (Icon.Play in supportedActions) addAction(Icon.Play, "Play this object") {
            val project = context[currentProject]
            val client = context[UDPSuperColliderClient]
            project.prepareForPlay(client)
            obj.play(client)
        }
        if (Icon.Delete in supportedActions) addAction(Icon.Delete, "Delete this object", ::delete)
        header.visibleProperty().bind(hoverProperty().or(selectedProperty))
    }

    open fun init(parent: ScoreView) {
        this.scoreView = parent
        this.context = parent.score.context
        layoutX = scoreView.getX(obj.start)
        layoutY = obj.y
        prefWidth = getDisplayWidth()
        prefHeight = obj.height
        renamedObject()
        recoloredObject()
        setupHeader()
        reassignedControls()
        obj.addView(this)
    }

    protected fun addAction(icon: Icon, action: String?, onAction: () -> Unit): Button {
        val button = icon.button(action = action)
        button.styleClass("score-object-btn")
        button.setOnAction { onAction() }
        actions.children.add(button)
        return button
    }

    open fun repaint() {
        reassignedControls()
    }

    protected open val defaultBorderColor: Color
        get() = BLACK

    open fun recoloredObject() {
        val borderColor = obj.associatedColor ?: defaultBorderColor
        contents.border = solidBorder(borderColor, width = 2.0, radius = 3.0)
    }

    fun renamedObject() {
        nameLabel.text = obj.name
    }

    fun muteToggled() {
        muteUnmuteBtn.graphic = if (obj.muted) Icon.Mute.getView() else Icon.Unmute.getView()
    }

    open fun reassignedControls() {
        for (editors in envelopeEditors) {
            editors.removeChildren()
            editors.dispose()
        }
        envelopeEditors.clear()
        if (obj.associatedEnvelopes.isEmpty()) return
        if (envelopesPane !in contents.children) contents.children.add(envelopesPane)
        setVgrow(envelopesPane, Priority.ALWAYS)
        for (control in obj.associatedEnvelopes) {
            if (!control.display) continue
            val parameter = control.parameter
            val envelope = control.envelope
            val e = EnvelopeEditor(parameter, envelope, envelopesPane, scoreView, obj)
            e.repaint()
            envelopeEditors.add(e)
        }
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

    fun setSelected(value: Boolean) {
        contents.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), value)
        selectedProperty.set(value)
    }

    private fun delete() {
        scoreView.score.removeObject(obj)
    }

    /*
    * Dragging and resizing
    * */

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
                scoreView.score.addObject(clone)
                scoreView.getObjectView(clone)
            } else this
            context[UndoManager].beginCompoundEdit()
            ev.consume()
        }
        addEventHandler(MouseEvent.MOUSE_PRESSED) { ev ->
            if (dragStart == null) {
                context[UndoManager].beginCompoundEdit()
                dragStart = Point(ev.screenX, ev.screenY)
                oldBounds = BoundingBox(layoutX, layoutY, prefWidth, prefHeight)
            }
            ev.consume()
        }
        addEventHandler(MouseEvent.MOUSE_DRAGGED) { ev ->
            val start = dragStart ?: return@addEventHandler
            val dx = ev.screenX - start.x
            val dy = ev.screenY - start.y
            if (isResizeCursor(cursor)) {
                resize(oldBounds!!, dx, dy, cursor, ev)
            } else {
                draggedObject.relocateBy(oldBounds!!, dx, dy)
            }
            ev.consume()
        }
        addEventHandler(MouseEvent.MOUSE_RELEASED) { ev ->
            if (draggedObject != this && draggedObject.boundsInParent == this.boundsInParent) {
                obj.container.removeObject(draggedObject.obj)
            }
            if (dragStart != null) {
                dragStart = null
                oldBounds = null
                draggedObject = this
                context[UndoManager].finishCompoundEdit("Move object")
            }
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
        setPrefSize(getDisplayWidth(), height)
        obj.height = height
        relocateObject(snappedX, y)
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
}