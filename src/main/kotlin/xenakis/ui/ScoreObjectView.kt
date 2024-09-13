package xenakis.ui

import hextant.context.Context
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
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.binding.map
import reaktive.value.binding.orElse
import reaktive.value.forEach
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Knob
import xenakis.model.*
import xenakis.model.InteractionSettings.SnapOption
import xenakis.sc.NumericalControlSpec
import xenakis.ui.ToolSelector.Tool
import xenakis.ui.XenakisController.Companion.currentProject

abstract class ScoreObjectView(val instance: ScoreObjectInstance) : VBox(), ScoreObjectInstance.Listener,
    SynthControls.View {
    var isInitialized: Boolean = false
        private set
    lateinit var pane: ScorePane
        private set
    protected val context: Context get() = pane.context

    private lateinit var nameEditor: NameControl
    private lateinit var muteUnmuteBtn: Button
    private val envelopeDisplayObservers = mutableMapOf<String, Observer>()
    val actions = HBox().centerChildrenVertically() styleClass "actions"
    private val knobControls = FlowPane().centerChildrenVertically() styleClass "knobs"

    protected val envelopesPane = Pane()
    private val envelopeEditors = mutableListOf<EnvelopeEditor>()

    protected open val defaultBackgroundColor: ReactiveValue<Color>
        get() = reactiveVariable(BLACK)
    protected val backgroundColor by lazy { instance.obj.associatedColor.orElse(defaultBackgroundColor) }
    protected open val borderColorWhenSelected: Color get() = backgroundColor.now.invert()
    protected open val nonSelectedBorderColor: Color get() = Color.GRAY
    protected val colorPicker: ColorPicker = ColorPicker() styleClass "button"

    init {
        styleClass("score-object")
        isFocusTraversable = true
        colorPicker.setFixedWidth(30.0)
        colorPicker.prefHeight = 30.0
    }

    protected open val supportedActions get() = listOf(Icon.Delete, Icon.Mute, Icon.Repeat)

    fun getDetailPane() = DetailPane().apply {
        nameEditor = NameControl(instance.obj)
        addItem("Name: ", nameEditor)
        setupDetailPane()
    }

    protected open fun DetailPane.setupDetailPane() {}

    private fun setupActions() {
        if (Icon.Repeat in supportedActions) addAction(Icon.Repeat, "Loop this object", ::createLoop)
        if (Icon.Mute in supportedActions) {
            val icon = if (instance.muted) Icon.Mute else Icon.Unmute
            muteUnmuteBtn = addAction(icon, "Toggle mute") {
                instance.toggleMuted()
            }
        }
        if (Icon.Delete in supportedActions) addAction(Icon.Delete, "Delete this object", ::delete)
    }

    fun createLoop() {
        val settings = context[currentProject].settings
        val grid = pane.getNearestGrid(layoutX, layoutY)?.obj.takeIf { settings.snapEnabled.now }
        val periodUnit = if (grid == null) null else settings.snapOption.now
        val periodInput = when (periodUnit) {
            null -> TextField(instance.duration.format(3))
            else -> {
                val default = (instance.duration / grid!!.getDuration(periodUnit) + 0.95).toInt()
                Spinner<Int>(1, Int.MAX_VALUE, default)
            }
        }
        val repetitionsInput = Spinner<Int>(1, 1000, 1, 1)
        val box = VBox(
            10.0,
            HBox(label("Loop period ($periodUnit): ").setFixedWidth(200.0), periodInput).centerChildrenVertically(),
            HBox(label("Number of repetitions").setFixedWidth(200.0), repetitionsInput).centerChildrenVertically()
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
                pane.score.loop(instance, period, repetitions)
            }
        }
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
        setupActions()
        children.add(envelopesPane)
        setVgrow(envelopesPane, Priority.ALWAYS)
        displayKnobs()
        instance.obj.addView(this)
        isInitialized = true
    }

    private fun setupSelecting() {
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            toFront()
            requestFocus()
            context[ScoreObjectSelectionManager].select(this, addToSelection = ev.isShiftDown)
            ev.consume()
        }
    }

    private fun initializeLayout() {
        layoutX = pane.getX(instance.position.time)
        layoutY = instance.position.y
        prefWidth = getDisplayWidth()
        prefHeight = instance.height
        instance.addListener(this)
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
            instance.obj.associatedColor.now = newColor
            context[UndoManager].record(ScoreObjectEdit.Recolor(instance.obj, oldColor, newColor))
        }
    }

    override fun toggledMute(muted: Boolean) {
        muteUnmuteBtn.graphic = if (instance.muted) Icon.Mute.getView() else Icon.Unmute.getView()
        setPseudoClassState("muted", instance.muted)
    }

    override fun removedControl(parameter: String, control: ParameterControl) {
        when (control) {
            is EnvelopeControl -> removedEnvelopeControl(parameter)
            is KnobControl -> removeKnob(parameter)
            else -> {}
        }
    }

    private fun removedEnvelopeControl(parameter: String) {
        removeEnvelope(parameter)
        envelopeDisplayObservers.remove(parameter)!!.kill()
    }

    override fun addedControl(parameter: String, control: ParameterControl) {
        when (control) {
            is EnvelopeControl -> addedDisplayControl(control, parameter)
            is KnobControl -> displayKnob(parameter, control)
            else -> {}
        }
    }

    private fun removeEnvelope(parameter: String) {
        val ed = envelopeEditors.find { ed -> ed.parameterName == parameter }
            ?: error("envelope editor for $parameter not found")
        ed.dispose()
        envelopeEditors.remove(ed)
    }

    private fun removeKnob(parameter: String) {
        knobControls.children.removeIf { k -> k is Knob && k.parameter == parameter }
    }

    private fun addedDisplayControl(control: EnvelopeControl, parameter: String) {
        if (control.display.now) displayEnvelope(parameter, control)
        envelopeDisplayObservers[parameter] = control.display.observe { _, _, display ->
            if (display) displayEnvelope(parameter, control)
            else removeEnvelope(parameter)
        }
    }

    private fun displayEnvelope(parameter: String, control: EnvelopeControl) {
        val envelope = control.envelope
        val e = EnvelopeEditor(parameter, envelope, this, envelopesPane, pane, instance.obj)
        e.repaint()
        envelopeEditors.add(e)
    }

    private fun displayKnobs() {
        knobControls.children.clear()
        for ((parameter, control) in instance.obj.associatedControls) {
            if (control !is KnobControl) continue
            displayKnob(parameter, control)
        }
    }

    private fun displayKnob(parameter: String, control: KnobControl) {
        val spec = instance.obj.getSpec(parameter) as NumericalControlSpec
        val knob = Knob(parameter, control, spec, radius = 24.0, context)
        knobControls.children.add(knob)
    }

    private fun setupCutting() {
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            if (context[XenakisUI].toolSelector.selected.value == Tool.Cut) {
                val cutPosition = pane.getDuration(ev.x - 0.0)
                context[UndoManager].beginCompoundEdit("Cut object")
                pane.score.removeObject(instance)
                val (leftHalf, rightHalf) = instance.cut(cutPosition) ?: return@addEventHandler
                pane.score.addObject(leftHalf)
                pane.score.addObject(rightHalf)
                context[UndoManager].finishCompoundEdit("Cut object")
                pane.selector.select(pane.getObjectView(leftHalf), addToSelection = false)
                pane.selector.select(pane.getObjectView(rightHalf), addToSelection = true)
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
        //highlight other instances
    }

    private fun setCloneOfSelected(value: Boolean) {
        setPseudoClassState("copy-of-selected", value)
    }

    private fun setOriginalOfSelected(value: Boolean) {
        setPseudoClassState("original-of-selected", value)
    }

    private fun delete() {
        pane.score.removeObject(instance)
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
        instance.moveTo(pane.getTime(x), y)
        pane.markX(x)
    }

    protected open fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor) {
        val newDur = pane.getDuration(width)
        if (!ev.isShiftDown) {
            val resizeFromLeft = cursor in setOf(Cursor.W_RESIZE, Cursor.NW_RESIZE, Cursor.SW_RESIZE)
            val resizeFromRight = cursor in setOf(Cursor.E_RESIZE, Cursor.NE_RESIZE, Cursor.SE_RESIZE)
            for ((parameter, ctrl) in instance.obj.associatedControls) {
                if (ctrl !is EnvelopeControl) continue
                val spec = instance.obj.getSpec(parameter) as NumericalControlSpec
                if (resizeFromLeft) ctrl.envelope.resize(newDur, HorizontalDirection.LEFT, spec)
                if (resizeFromRight) ctrl.envelope.resize(newDur, HorizontalDirection.RIGHT, spec)
            }
        } else {
            for ((_, ctrl) in instance.obj.associatedControls) {
                if (ctrl !is EnvelopeControl) continue
                ctrl.envelope.rescale(newDur)
            }
        }
        instance.obj.duration = newDur
        instance.obj.height = height
    }

    protected open fun beforeResize(ev: MouseEvent, cursor: Cursor) {
        context[ScoreObjectSelectionManager].select(this, addToSelection = ev.isShiftDown)
    }

    open fun getDisplayWidth(): Double = pane.getWidth(instance.duration)

    open fun rescale() {
        repaintEnvelopes()
    }

    protected fun repaintEnvelopes() {
        for (e in envelopeEditors) {
            e.repaint()
        }
    }

    final override fun moved(start: Double, y: Double) {
        relocate(pane.getX(instance.time), instance.y)
    }

    fun resized() {
        setPrefSize(getDisplayWidth(), instance.height)
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
        val oldHeight = instance.height

        if (cursor.resizeFromLeft) newWidth = newWidth.coerceAtMost(oldWidth + old.minX)
        if (pane is SubScorePane && cursor.resizeFromRight) newWidth = newWidth.coerceAtMost(pane.width - old.minX)
        if (cursor.resizeFromTop) newHeight = newHeight.coerceAtMost(oldHeight + old.minY)
        if (cursor.resizeFromBottom) newHeight = newHeight.coerceAtMost(pane.height - old.minY)

        resizeObject(newWidth, newHeight, ev, cursor)
        context[UndoManager].record(ResizeEdit(this, oldWidth, oldHeight, newWidth, newHeight, ev, cursor))
        val newX =
            if (cursor.resizeFromLeft)
                instance.time + pane.getDuration(oldWidth - getDisplayWidth())
            else instance.time
        val newY = if (cursor.resizeFromTop) instance.y + (oldHeight - instance.height) else instance.y
        instance.moveTo(newX, newY)
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
}
