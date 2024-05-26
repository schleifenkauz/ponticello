package xenakis.ui

import hextant.context.Context
import hextant.fx.label
import hextant.undo.UndoManager
import javafx.beans.property.SimpleBooleanProperty
import javafx.css.PseudoClass
import javafx.geometry.BoundingBox
import javafx.geometry.Bounds
import javafx.geometry.HorizontalDirection
import javafx.scene.Cursor
import javafx.scene.control.*
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.paint.Color.BLACK
import xenakis.impl.Knob
import xenakis.impl.Point
import xenakis.impl.UDPSuperColliderClient
import xenakis.model.ClonedObject
import xenakis.model.KnobControl
import xenakis.model.NamingManager
import xenakis.model.ScoreObject
import xenakis.sc.NumericalControlSpec
import xenakis.ui.ToolSelector.Tool
import xenakis.ui.XenakisController.Companion.currentProject
import kotlin.math.absoluteValue

abstract class ScoreObjectView(var myObject: ScoreObject) : VBox(), PositionListener {
    private var dragStart: Point? = null
    private var oldBounds: Bounds? = null
    private var draggedObject: ScoreObjectView = this

    lateinit var pane: ScorePane
        private set
    protected lateinit var context: Context

    private val nameLabel = Label().styleClass("score-object-name")
    private lateinit var muteUnmuteBtn: Button
    val header = HBox(20.0).centerChildrenVertically()
    private val actions = HBox(10.0).centerChildrenVertically()
    protected val envelopesPane = Pane()

    private val envelopeEditors = mutableListOf<EnvelopeEditor>()
    private val knobControls = HBox(10.0)

    private val selectedProperty = SimpleBooleanProperty(false)

    protected open val canUserChangeHeight: Boolean get() = true
    protected open val canUserChangeWidth: Boolean get() = true

    init {
        styleClass("score-object")
        envelopesPane.widthProperty().addListener { _ -> rescale() }
        envelopesPane.heightProperty().addListener { _ -> rescale() }
    }

    protected open val supportedActions get() = listOf(Icon.Delete, Icon.Play, Icon.Mute, Icon.Repeat)

    private fun setupActions() {
        header.children.add(actions)
        if (Icon.Repeat in supportedActions) addAction(Icon.Repeat, "Loop this object", ::createLoop)
        if (Icon.Mute in supportedActions) {
            val icon = if (myObject.muted) Icon.Mute else Icon.Unmute
            muteUnmuteBtn = addAction(icon, "Toggle mute") {
                myObject.muted = !myObject.muted
            }
        }
        if (Icon.Play in supportedActions) addAction(Icon.Play, "Play this object", ::playMyObject)
        if (Icon.Delete in supportedActions) addAction(Icon.Delete, "Delete this object", ::delete)
    }

    fun playMyObject() {
        val project = context[currentProject]
        val client = context[UDPSuperColliderClient]
        project.prepareForPlay(client)
        myObject.play(client)
    }

    fun createLoop() {
        val periodInput = TextField(myObject.duration.format(2))
        val numberOfRepeats = Spinner<Int>(1, 1000, 1, 1)
        val box = GridPane().apply {
            add(label("Loop period (s): "), 0, 0)
            add(periodInput, 0, 1)
            add(label("Number of repetitions"), 1, 0)
            add(numberOfRepeats, 1, 1)
        }
        box.showDialog(
            "Loop configuration", context,
            extraConfig = {
                val btnOk = dialogPane.lookupButton(ButtonType.OK)
                btnOk.disableProperty().bind(periodInput.textProperty().map { txt ->
                    val v = txt.toDoubleOrNull()
                    v == null || v == 0.0
                })
            }
        ) { btn ->
            if (btn == ButtonType.OK) {
                val period = periodInput.text.toDouble()
                val repetitions = numberOfRepeats.value
                pane.score.loop(myObject, period, repetitions)
            }
        }
    }

    open fun initialize(parent: ScorePane) {
        this.pane = parent
        this.context = parent.score.context
        layoutX = pane.getX(myObject.position.start)
        layoutY = myObject.position.y
        prefWidth = getDisplayWidth()
        prefHeight = myObject.height
        alwaysUpdateCursor()
        setupDragging()
        renamedObject()
        recoloredObject()
        setupCutting()
        header.children.add(nameLabel)
        setupActions()
        reassignedControls()
        myObject.addView(this)
        myObject.position.addListener(this)
    }

    protected fun addAction(icon: Icon, action: String?, onAction: () -> Unit): Button {
        val button = icon.button(action = action)
        button.styleClass("score-object-btn")
        button.setOnAction { onAction() }
        actions.children.add(button)
        return button
    }

    protected open val defaultBorderColor: Color
        get() = BLACK

    open fun recoloredObject() {
        val borderColor = myObject.associatedColor ?: defaultBorderColor
        border = solidBorder(borderColor, width = 2.0, radius = 3.0)
    }

    fun renamedObject() {
        nameLabel.text = myObject.name
    }

    open fun muteToggled() {
        muteUnmuteBtn.graphic = if (myObject.muted) Icon.Mute.getView() else Icon.Unmute.getView()
        pseudoClassStateChanged(PseudoClass.getPseudoClass("muted"), myObject.muted)
    }

    open fun reassignedControls() {
        displayEnvelopes()
        displayKnobs()
    }

    private fun displayKnobs() {
        knobControls.children.clear()
        for (control in myObject.controls) {
            if (control !is KnobControl) continue
            val spec = myObject.getSpec(control.parameter) as NumericalControlSpec
            val knob = Knob(control, spec)
            knobControls.children.add(knob)
        }
        if (knobControls.children.any()) {
            if (knobControls !in header.children) {
                header.children.add(1, knobControls)
            }
        } else header.children.remove(knobControls)
    }

    private fun displayEnvelopes() {
        for (editors in envelopeEditors) {
            editors.removeChildren()
            editors.dispose()
        }
        envelopeEditors.clear()
        if (myObject.associatedEnvelopes.isEmpty()) return
        if (envelopesPane !in children) children.add(envelopesPane)
        setVgrow(envelopesPane, Priority.ALWAYS)
        for (control in myObject.associatedEnvelopes) {
            if (!control.display) continue
            val parameter = control.parameter
            val envelope = control.envelope
            val e = EnvelopeEditor(parameter, envelope, envelopesPane, pane, myObject)
            e.repaint()
            envelopeEditors.add(e)
        }
        return
    }

    private fun setupCutting() {
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            if (context[XenakisUI].toolSelector.selected.value == Tool.Cut) {
                val cutPosition = pane.getDuration(ev.x - 0.0)
                val leftHalf = myObject.cut(cutPosition, HorizontalDirection.LEFT, "${myObject.name}_left")
                val rightHalf = myObject.cut(cutPosition, HorizontalDirection.RIGHT, "${myObject.name}_right")
                if (leftHalf == null || rightHalf == null) return@addEventHandler
                context[UndoManager].beginCompoundEdit("Cut object")
                pane.score.removeObject(myObject)
                pane.score.addObject(leftHalf)
                pane.score.addObject(rightHalf)
                context[UndoManager].finishCompoundEdit("Cut object")
                ev.consume()
            }
        }
    }

    fun setSelected(value: Boolean) {
        pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), value)
        selectedProperty.set(value)
        for (obj in pane.score.objects) {
            if (obj is ClonedObject && obj.original == myObject) {
                pane.getObjectView(obj).setCloneOfSelected(value)
            }
            if (myObject is ClonedObject && (myObject as ClonedObject).original == obj) {
                pane.getObjectView(obj).setOriginalOfSelected(value)
            }
        }
    }

    private fun setCloneOfSelected(value: Boolean) {
        pseudoClassStateChanged(PseudoClass.getPseudoClass("clone-of-selected"), value)
    }

    private fun setOriginalOfSelected(value: Boolean) {
        pseudoClassStateChanged(PseudoClass.getPseudoClass("original-of-selected"), value)
    }

    private fun delete() {
        pane.score.removeObject(myObject)
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
        addEventHandler(MouseEvent.MOUSE_PRESSED) { ev ->
            if (context[XenakisUI].toolSelector.selected.value != Tool.Pointer) return@addEventHandler
            if (dragStart == null) {
                oldBounds = BoundingBox(layoutX, layoutY, prefWidth, prefHeight)
                dragStart = Point(ev.screenX, ev.screenY)
                draggedObject = if (ev.isShiftDown) {
                    val copy = myObject.copy(newName = context[NamingManager].nameForCopy(myObject))
                    context[UndoManager].beginCompoundEdit("Copy object")
                    pane.score.addObject(copy)
                    pane.getObjectView(copy)
                } else if (ev.isControlDown) {
                    val clone = myObject.clone(
                        name = context[NamingManager].nameForClone(myObject)
                    )
                    context[UndoManager].beginCompoundEdit("Clone object")
                    pane.score.addObject(clone)
                    pane.getObjectView(clone)
                } else {
                    context[UndoManager].beginCompoundEdit("Move object")
                    this
                }
            }
            ev.consume()
        }
        addEventHandler(MouseEvent.MOUSE_DRAGGED) { ev ->
            if (context[XenakisUI].toolSelector.selected.value != Tool.Pointer) return@addEventHandler
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
            if (context[XenakisUI].toolSelector.selected.value != Tool.Pointer) return@addEventHandler
            if (draggedObject != this && draggedObject.boundsInParent == this.boundsInParent) {
                myObject.parent.removeObject(draggedObject.myObject)
            }
            if (dragStart != null) {
                dragStart = null
                oldBounds = null
                draggedObject = this
                context[UndoManager].finishCompoundEdit()
            }
            ev.consume()
        }
    }

    private fun isResizeCursor(cursor: Cursor) = cursor.toString().endsWith("RESIZE")

    private fun isInParentBounds(x: Double, y: Double, width: Double, height: Double) =
        x >= 0.0 && y >= 0.0 && x + width <= pane.width && y + height <= pane.height

    private fun relocateBy(old: Bounds, dx: Double, dy: Double) {
        val x = (old.minX + dx).snap(pane.timeSnap)
        val y = (old.minY + dy)
        if (!isInParentBounds(x, y, prefWidth, prefHeight)) return
        relocateObject(x, y)
    }

    protected open fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor): Boolean {
        myObject.duration = pane.getDuration(width)
        myObject.height = height
        return true
    }

    open fun getDisplayWidth(): Double = pane.getWidth(myObject.duration)

    open fun rescale() {
        rescaleEnvelopes()
    }

    private fun rescaleEnvelopes() {
        for (e in envelopeEditors) {
            e.repaint()
        }
    }

    private fun relocateObject(x: Double, y: Double) {
        pane.score.moveObject(myObject, pane.getTime(x), y)
    }

    override fun moved(obj: ScoreObject, start: Double, y: Double) {
        relocate(pane.getX(myObject.start), myObject.y)
    }

    private fun updateCursor(x: Double, y: Double) {
        val tx = 5
        val ty = 5
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

    private fun resize(x: Double, y: Double, width: Double, height: Double, ev: MouseEvent, cursor: Cursor) {
        val snappedX = x.snap(pane.timeSnap)
        val snappedWidth = width.snap(pane.timeSnap)
        if (snappedWidth < 10.0 || height < 10.0) return
        if (!isInParentBounds(snappedX, y, snappedWidth, height)) return
        if (resizeObject(snappedWidth, height, ev, cursor))
            relocateObject(snappedX, y)
    }

    fun resized() {
        setPrefSize(getDisplayWidth(), myObject.height)
    }

    private fun resize(old: Bounds, dx: Double, dy: Double, cursor: Cursor, ev: MouseEvent) {
        when (cursor) {
            Cursor.NW_RESIZE -> resize(old.minX + dx, old.minY + dy, old.width - dx, old.height - dy, ev, cursor)
            Cursor.N_RESIZE -> resize(old.minX, old.minY + dy, old.width, old.height - dy, ev, cursor)
            Cursor.NE_RESIZE -> resize(old.minX, old.minY + dy, old.width + dx, old.height - dy, ev, cursor)
            Cursor.E_RESIZE -> resize(old.minX, old.minY, old.width + dx, old.height, ev, cursor)
            Cursor.SE_RESIZE -> resize(old.minX, old.minY, old.width + dx, old.height + dy, ev, cursor)
            Cursor.S_RESIZE -> resize(old.minX, old.minY, old.width, old.height + dy, ev, cursor)
            Cursor.SW_RESIZE -> resize(old.minX + dx, old.minY, old.width - dx, old.height + dy, ev, cursor)
            Cursor.W_RESIZE -> resize(old.minX + dx, old.minY, old.width - dx, old.height, ev, cursor)
        }
    }
}