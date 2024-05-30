package xenakis.ui

import hextant.context.Context
import hextant.fx.label
import hextant.undo.UndoManager
import javafx.css.PseudoClass
import javafx.geometry.BoundingBox
import javafx.geometry.Bounds
import javafx.geometry.HorizontalDirection
import javafx.scene.Cursor
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.Spinner
import javafx.scene.control.TextField
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.paint.Color.BLACK
import reaktive.value.now
import xenakis.impl.Knob
import xenakis.impl.Point
import xenakis.impl.SuperColliderClient
import xenakis.model.*
import xenakis.sc.NumericalControlSpec
import xenakis.ui.ToolSelector.Tool
import xenakis.ui.XenakisController.Companion.currentProject
import kotlin.math.absoluteValue

abstract class ScoreObjectView(var myObject: ScoreObject) : VBox(), PositionListener {
    private var dragStart: Point? = null
    private var oldBounds: Bounds? = null

    lateinit var pane: ScorePane
        private set
    protected lateinit var context: Context

    private lateinit var nameEditor: NameControl
    private lateinit var muteUnmuteBtn: Button
    val header = HBox(20.0).centerChildrenVertically()
    private val actions = HBox(10.0).centerChildrenVertically()
    protected val envelopesPane = Pane()

    private val envelopeEditors = mutableListOf<EnvelopeEditor>()
    private val knobControls = HBox(10.0)

    private lateinit var window: SubWindow

    protected open val canUserChangeHeight: Boolean get() = true
    protected open val canUserChangeWidth: Boolean get() = true

    init {
        styleClass("score-object")
        envelopesPane.widthProperty().addListener { _ -> rescale() }
        envelopesPane.heightProperty().addListener { _ -> rescale() }
    }

    protected open val supportedActions get() = listOf(Icon.Delete, Icon.Play, Icon.Mute, Icon.Repeat, Icon.ExtraWindow)

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
        if (Icon.ExtraWindow in supportedActions) addAction(Icon.ExtraWindow, "Open in extra window") { window.show() }
    }

    fun playMyObject() {
        val project = context[currentProject]
        val client = context[SuperColliderClient]
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
        recoloredObject()
        setupCutting()
        nameEditor = NameControl(myObject)
        header.children.add(nameEditor)
        setupActions()
        displayEnvelopes()
        displayKnobs()
        myObject.addView(this)
        myObject.position.addListener(this)
        window = SubWindow(this, myObject.name.now, context, SubWindow.Type.Modal, parent = pane) {
            title = myObject.name.now
        }
    }

    protected fun addAction(icon: Icon, action: String?, onAction: () -> Unit): Button {
        val button = icon.button(action = action)
        button.styleClass("score-object-btn")
        button.setOnAction { onAction() }
        actions.children.add(button)
        return button
    }

    protected open val defaultBackgroundColor: Color
        get() = BLACK

    open fun recoloredObject() {
        val backgroundColor = myObject.associatedColor ?: defaultBackgroundColor
        background = Background(BackgroundFill(backgroundColor, CornerRadii.EMPTY, null))
    }

    open fun muteToggled() {
        muteUnmuteBtn.graphic = if (myObject.muted) Icon.Mute.getView() else Icon.Unmute.getView()
        pseudoClassStateChanged(PseudoClass.getPseudoClass("muted"), myObject.muted)
    }

    fun reassignedControl(parameter: String, oldControl: ParameterControl, newControl: ParameterControl) {
        removedControl(parameter, oldControl)
        addedControl(parameter, newControl)
    }

    fun removedControl(parameter: String, oldControl: ParameterControl) {
        when (oldControl) {
            is EnvelopeControl -> removeEnvelope(parameter)
            is KnobControl -> removeKnob(parameter)
            else -> {}
        }
    }

    fun addedControl(parameter: String, newControl: ParameterControl) {
        when (newControl) {
            is EnvelopeControl -> displayEnvelope(parameter, newControl)
            is KnobControl -> displayKnob(parameter, newControl)
            else -> {}
        }
    }

    private fun removeEnvelope(parameter: String) {
        val ed = envelopeEditors.find { ed -> ed.parameterName == parameter }
            ?: error("envelope editor for $parameter not found")
        ed.dispose()
        envelopeEditors.remove(ed)
        if (envelopeEditors.isEmpty()) children.remove(envelopesPane)
    }

    private fun removeKnob(parameter: String) {
        knobControls.children.removeIf { k -> k is Knob && k.parameter == parameter }
        if (knobControls.children.isEmpty()) header.children.remove(knobControls)
    }

    private fun displayEnvelopes() {
        setVgrow(envelopesPane, Priority.ALWAYS)
        for ((parameter, control) in myObject.associatedControls) {
            if (control !is EnvelopeControl || !control.display) continue
            displayEnvelope(parameter, control)
        }
        val anyEnvelopes = envelopeEditors.any()
        if (envelopesPane !in children && anyEnvelopes) children.add(envelopesPane)
        if (envelopesPane in children && !anyEnvelopes) children.remove(envelopesPane)
    }

    private fun displayEnvelope(parameter: String, control: EnvelopeControl) {
        val envelope = control.envelope
        val e = EnvelopeEditor(parameter, envelope, envelopesPane, pane, myObject)
        e.repaint()
        envelopeEditors.add(e)
        if (envelopesPane !in children) children.add(envelopesPane)
    }

    private fun displayKnobs() {
        knobControls.children.clear()
        for ((parameter, control) in myObject.associatedControls) {
            if (control !is KnobControl) continue
            displayKnob(parameter, control)
        }
        if (knobControls !in header.children) header.children.add(knobControls)
    }

    private fun displayKnob(parameter: String, control: KnobControl) {
        val spec = myObject.getSpec(parameter) as NumericalControlSpec
        val knob = Knob(parameter, control, spec, radius = 24.0, context)
        knobControls.children.add(knob)
    }

    private fun setupCutting() {
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            if (context[XenakisUI].toolSelector.selected.value == Tool.Cut) {
                val cutPosition = pane.getDuration(ev.x - 0.0)
                val leftHalf = myObject.cut(cutPosition, HorizontalDirection.LEFT, "${myObject.name.now}_left")
                val rightHalf = myObject.cut(cutPosition, HorizontalDirection.RIGHT, "${myObject.name.now}_right")
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
        if (value) {
            val backgroundFill = myObject.associatedColor ?: defaultBackgroundColor
            border = solidBorder(backgroundFill.invert(), width = 4.0)
        } else {
            border = null
        }
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
        pseudoClassStateChanged(PseudoClass.getPseudoClass("copy-of-selected"), value)
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
                if (isResizeCursor(cursor)) context[UndoManager].beginCompoundEdit("Resize object")
                else context[UndoManager].beginCompoundEdit("Move object")
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
                relocateBy(oldBounds!!, dx, dy)
            }
            ev.consume()
        }
        addEventHandler(MouseEvent.MOUSE_RELEASED) { ev ->
            if (context[XenakisUI].toolSelector.selected.value != Tool.Pointer) return@addEventHandler
            if (dragStart != null) {
                dragStart = null
                oldBounds = null
                context[UndoManager].finishCompoundEdit()
            }
            ev.consume()
        }
    }

    private fun isResizeCursor(cursor: Cursor) = cursor.toString().endsWith("RESIZE")

    private fun isInParentBounds(x: Double, y: Double, width: Double, height: Double) =
        x >= 0.0 && y >= 0.0 && x + width <= pane.width && y + height <= pane.height

    private fun relocateBy(old: Bounds, dx: Double, dy: Double) {
        var x = (old.minX + dx).snap(pane.timeSnap)
        var y = (old.minY + dy)
        x = x.coerceIn(0.0, pane.width - width)
        y = y.coerceIn(0.0, pane.height - height)
        relocateObject(x, y)
    }

    protected open fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor) {
        val newDur = pane.getDuration(width)
        if (!ev.isShiftDown) {
            val resizeFromLeft = cursor in setOf(Cursor.W_RESIZE, Cursor.NW_RESIZE, Cursor.SW_RESIZE)
            val resizeFromRight = cursor in setOf(Cursor.E_RESIZE, Cursor.NE_RESIZE, Cursor.SE_RESIZE)
            for ((parameter, ctrl) in myObject.associatedControls) {
                if (ctrl !is EnvelopeControl) continue
                val spec = myObject.getSpec(parameter) as NumericalControlSpec
                if (resizeFromLeft) ctrl.envelope.resize(newDur, HorizontalDirection.LEFT, spec)
                if (resizeFromRight) ctrl.envelope.resize(newDur, HorizontalDirection.RIGHT, spec)
            }
        } else {
            for ((_, ctrl) in myObject.associatedControls) {
                if (ctrl !is EnvelopeControl) continue
                ctrl.envelope.rescale(newDur)
            }
        }
        myObject.duration = newDur
        myObject.height = height
    }

    open fun getDisplayWidth(): Double = pane.getWidth(myObject.duration)

    open fun rescale() {
        repaintEnvelopes()
    }

    protected fun repaintEnvelopes() {
        for (e in envelopeEditors) {
            e.repaint()
        }
    }

    private fun relocateObject(x: Double, y: Double) {
        pane.score.moveObject(myObject, pane.getTime(x), y)
    }

    final override fun moved(obj: ScoreObject, start: Double, y: Double) {
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
        val oldWidth = getDisplayWidth()
        val oldHeight = myObject.height
        resizeObject(snappedWidth, height, ev, cursor)
        if (cursor.resizeFromLeft) myObject.position.start += pane.getDuration(oldWidth - getDisplayWidth())
        if (cursor.resizeFromTop) myObject.position.y += oldHeight - myObject.height
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
