package xenakis.ui

import hextant.context.Context
import hextant.fx.initHextantScene
import hextant.fx.label
import hextant.undo.AbstractEdit
import hextant.undo.Edit
import hextant.undo.UndoManager
import javafx.geometry.Bounds
import javafx.geometry.HorizontalDirection
import javafx.scene.Cursor
import javafx.scene.control.*
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.paint.Color.BLACK
import org.controlsfx.control.ToggleSwitch
import reaktive.value.ReactiveValue
import reaktive.value.binding.map
import reaktive.value.binding.orElse
import reaktive.value.forEach
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Knob
import xenakis.impl.SuperColliderClient
import xenakis.model.*
import xenakis.model.InteractionSettings.SnapOption
import xenakis.sc.NumericalControlSpec
import xenakis.ui.ToolSelector.Tool
import xenakis.ui.XenakisController.Companion.currentProject
import java.util.logging.Logger

abstract class ScoreObjectView(var myObject: ScoreObject) : VBox(), PositionListener {
    var isInitialized: Boolean = false
        private set
    lateinit var pane: ScorePane
        private set
    protected val context: Context get() = pane.context

    private lateinit var nameEditor: NameControl
    private lateinit var muteUnmuteBtn: Button
    val header = HBox(20.0).centerChildrenVertically()
    private val actions = HBox(10.0).centerChildrenVertically()
    protected val envelopesPane = Pane()

    private val envelopeEditors = mutableListOf<EnvelopeEditor>()
    private val knobControls = HBox(10.0)

    protected open val defaultBackgroundColor: ReactiveValue<Color>
        get() = reactiveVariable(BLACK)
    protected val backgroundColor by lazy { myObject.associatedColor.orElse(defaultBackgroundColor) }
    protected open val borderColorWhenSelected: Color get() = backgroundColor.now.invert()
    protected open val nonSelectedBorderColor: Color get() = Color.GRAY
    protected val colorPicker: ColorPicker = ColorPicker() styleClass "button"

    private lateinit var window: SubWindow

    init {
        styleClass("score-object")
        colorPicker.prefWidth = 30.0
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
        val beforePlay = project.serverTree.editor.result.now
        context[SuperColliderClient].run {
            beforePlay.code(this)
            myObject.play(this)
        }
    }

    fun createLoop() {
        val settings = context[currentProject].settings
        val grid = pane.getNearestGrid(layoutX, layoutY)?.obj.takeIf { settings.snapEnabled.now }
        val periodUnit = if (grid == null) null else settings.snapOption.now
        val periodInput = when (periodUnit) {
            null -> TextField(myObject.duration.format(3))
            else -> {
                val default = (myObject.duration / grid!!.getDuration(periodUnit) + 0.95).toInt()
                Spinner<Int>(1, Int.MAX_VALUE, default)
            }
        }
        val switchChainArrows = ToggleSwitch("Create chain arrows")
        val repetitionsInput = Spinner<Int>(1, 1000, 1, 1)
        val box = VBox(
            10.0,
            HBox(label("Loop period ($periodUnit): ").setPreferredWidth(200.0), periodInput).centerChildrenVertically(),
            HBox(label("Number of repetitions").setPreferredWidth(200.0), repetitionsInput).centerChildrenVertically(),
            switchChainArrows
        )
        box.showDialog(
            "Loop configuration", context,
            extraConfig = {
                if (periodInput is TextField) {
                    val btnOk = dialogPane.lookupButton(ButtonType.OK)
                    btnOk.disableProperty().bind(periodInput.textProperty().map { txt ->
                        val v = txt.toDoubleOrNull()
                        v == null || v == 0.0
                    })
                }
            }
        ) { btn ->
            if (btn == ButtonType.OK) {
                val period = when (periodInput) {
                    is Spinner<*> -> periodInput.value as Int * grid!!.getDuration(periodUnit ?: SnapOption.Seconds)
                    is TextField -> periodInput.text.toDouble()
                    else -> error("Invalid period input control: $periodInput")
                }
                val repetitions = repetitionsInput.value
                pane.score.loop(myObject, period, repetitions, switchChainArrows.isSelected)
            }
        }
    }

    protected open fun getSubWindowView(): Region = pane.createObjectView(myObject).also { v ->
        v.pane = pane
        v.displayEnvelopes()
        v.displayKnobs()
        v.myObject.addView(v)
    }

    fun showInSubWindow() {
        window.show()
    }

    open fun initialize(parent: ScorePane) {
        this.pane = parent
        initializeLayout()
        setupSelecting()
        setupDraggingAndResizing(
            parent,
            canUserChangeWidth = true, canUserChangeHeight = true, Tool.Pointer,
            beforeResize = this::beforeResize,
            relocateBy = this::relocateBy, resize = this::resize
        )
        setBackground()
        border = solidBorder(nonSelectedBorderColor, width = 2.0)
        setupCutting()
        initializeHeader()
        displayEnvelopes()
        displayKnobs()
        myObject.addView(this)
        isInitialized = true
    }

    private fun setupSelecting() {
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            toFront()
            context[ScoreObjectSelector].select(this, addToSelection = ev.isShiftDown)
            ev.consume()
        }
    }

    private fun initializeHeader() {
        nameEditor = NameControl(myObject)
        header.children.add(nameEditor)
        setupActions()
    }

    private fun initializeLayout() {
        layoutX = pane.getX(myObject.position.start)
        layoutY = myObject.position.y
        prefWidth = getDisplayWidth()
        prefHeight = myObject.height
        myObject.position.addListener(this)
    }

    private fun setupSubWindow() {
        val viewInSubWindow = getSubWindowView()
        window = SubWindow(viewInSubWindow, myObject.name.now, context, SubWindow.Type.Modal)
        window.titleProperty().bind(myObject.name.asObservableValue())
        window.width = 1000.0
        window.height = 1000.0
        window.scene.initHextantScene(context)
        //viewInSubWindow.prefWidthProperty().bind(window.widthProperty())
        //viewInSubWindow.prefHeightProperty().bind(window.heightProperty())
    }

    protected fun addAction(icon: Icon, action: String?, onAction: () -> Unit): Button {
        val button = icon.button(action = action)
        button.styleClass("score-object-btn")
        button.setOnAction { onAction() }
        actions.children.add(button)
        return button
    }

    private fun setBackground() {
        backgroundProperty().bind(backgroundColor.map { color ->
            Background(BackgroundFill(color, CornerRadii.EMPTY, null))
        }.asObservableValue())
        colorPicker.userData = backgroundColor.forEach { color ->
            colorPicker.value = color
        }
        colorPicker.valueProperty().addListener { _, oldColor, newColor ->
            myObject.associatedColor.now = newColor
            context[UndoManager].record(ScoreObjectEdit.Recolor(myObject, oldColor, newColor))
        }
    }

    open fun muteToggled() {
        muteUnmuteBtn.graphic = if (myObject.muted) Icon.Mute.getView() else Icon.Unmute.getView()
        setPseudoClassState("muted", myObject.muted)
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
                pane.selector.select(pane.getObjectView(leftHalf), addToSelection = false)
                pane.selector.select(pane.getObjectView(rightHalf), addToSelection = true)
                context[UndoManager].finishCompoundEdit("Cut object")
                ev.consume()
            }
        }
    }

    fun setSelected(value: Boolean) {
        border = if (value) {
            solidBorder(borderColorWhenSelected, width = 2.0)
        } else {
            solidBorder(nonSelectedBorderColor, width = 2.0)
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
        setPseudoClassState("copy-of-selected", value)
    }

    private fun setOriginalOfSelected(value: Boolean) {
        setPseudoClassState("original-of-selected", value)
    }

    private fun delete() {
        pane.score.removeObject(myObject)
    }

    /*
    * Dragging and resizing
    * */

    private fun relocateBy(old: Bounds, dx: Double, dy: Double) {
        var (x, y) = pane.snapToGrid(old.minX + dx, (old.minY + dy))
        x = x.coerceAtLeast(0.0)
        y = y.coerceAtLeast(0.0)
        if (pane is SubScorePane) x = x.coerceAtMost(pane.width - old.width)
        y = y.coerceAtMost(pane.height - old.height)
        pane.score.moveObject(myObject, pane.getTime(x), y)
        pane.markX(x)
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

    protected open fun beforeResize(ev: MouseEvent, cursor: Cursor) {
        context[ScoreObjectSelector].select(this, addToSelection = ev.isShiftDown)
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

    final override fun moved(obj: ScoreObject, start: Double, y: Double) {
        relocate(pane.getX(myObject.start), myObject.y)
    }

    fun resized() {
        setPrefSize(getDisplayWidth(), myObject.height)
        rescale()
    }

    private fun resize(old: Bounds, deltaX: Double, deltaY: Double, cursor: Cursor, ev: MouseEvent) {
        val oldX = if (cursor.resizeFromLeft) old.minX else old.maxX
        val oldY = if (cursor.resizeFromTop) old.minY else old.maxY
        val (x, y) = pane.snapToGrid(oldX + deltaX, oldY + deltaY)
        pane.markX(x)
        val dx = x - oldX
        val dy = y - oldY
        var newWidth = old.width
        if (cursor.resizeFromLeft) newWidth -= dx
        else if (cursor.resizeFromRight) newWidth += dx
        var newHeight = old.height
        if (cursor.resizeFromTop) newHeight -= dy
        else if (cursor.resizeFromBottom) newHeight += dy

        newWidth = newWidth.coerceAtLeast(10.0)
        newHeight = newHeight.coerceAtLeast(10.0)

        val oldWidth = getDisplayWidth()
        val oldHeight = myObject.height

        if (cursor.resizeFromLeft) newWidth = newWidth.coerceAtMost(oldWidth + old.minX)
        if (pane is SubScorePane && cursor.resizeFromRight) newWidth = newWidth.coerceAtMost(pane.width - old.minX)
        if (cursor.resizeFromTop) newHeight = newHeight.coerceAtMost(oldHeight + old.minY)
        if (cursor.resizeFromBottom) newHeight = newHeight.coerceAtMost(pane.height - old.minY)

        resizeObject(newWidth, newHeight, ev, cursor)
        context[UndoManager].record(ResizeEdit(this, oldWidth, oldHeight, newWidth, newHeight, ev, cursor))
        val newX =
            if (cursor.resizeFromLeft)
                myObject.start + pane.getDuration(oldWidth - getDisplayWidth())
            else myObject.start
        val newY = if (cursor.resizeFromTop) myObject.y + (oldHeight - myObject.height) else myObject.y
        pane.score.moveObject(myObject, newX, newY)
    }

    private class ResizeEdit(
        private val obj: ScoreObjectView,
        private val oldWidth: Double,
        private val oldHeight: Double,
        private val newWidth: Double,
        private val newHeight: Double,
        private val ev: MouseEvent,
        private val cursor: Cursor
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Resize object"

        override fun doUndo() {
            obj.resizeObject(oldWidth, oldHeight, ev, cursor)
        }

        override fun doRedo() {
            obj.resizeObject(newWidth, newHeight, ev, cursor)
        }

        override fun mergeWith(other: Edit): Edit? =
            if (other is ResizeEdit && other.obj == this.obj && other.cursor == this.cursor && other.ev.isShiftDown == this.ev.isShiftDown)
                ResizeEdit(obj, oldWidth, oldHeight, other.newWidth, other.newHeight, ev, cursor)
            else null
    }

    companion object {
        private val logger = Logger.getLogger("ScoreObjectView")
    }
}
