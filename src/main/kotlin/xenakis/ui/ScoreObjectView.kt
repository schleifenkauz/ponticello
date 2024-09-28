package xenakis.ui

import hextant.context.Context
import hextant.undo.UndoManager
import javafx.geometry.Bounds
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.VerticalDirection
import javafx.scene.Cursor
import javafx.scene.control.Button
import javafx.scene.control.ColorPicker
import javafx.scene.control.Spinner
import javafx.scene.control.TextField
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
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.ui.ToolSelector.Tool
import xenakis.ui.XenakisController.Companion.currentProject
import xenakis.ui.prompt.compoundInput

abstract class ScoreObjectView(
    val instance: ScoreObjectInstance
) : Pane(), ScoreObjectInstance.Listener, SynthControls.View, ScoreObject.Listener {
    var isInitialized: Boolean = false
        private set
    lateinit var pane: ScorePane
        private set
    val context: Context get() = pane.context

    private lateinit var nameEditor: NameControl
    private lateinit var muteUnmuteBtn: Button
    private val envelopeDisplayObservers = mutableMapOf<String, Observer>()
    val actions = HBox().centerChildrenVertically() styleClass "actions"
    private val knobControls = FlowPane().centerChildrenVertically() styleClass "knobs" //TODO remove

    private val envelopeEditors = mutableListOf<EnvelopeEditor>()

    protected open val defaultBackgroundColor: ReactiveValue<Color>
        get() = reactiveVariable(BLACK)
    val backgroundColor by lazy { instance.obj.associatedColor.orElse(defaultBackgroundColor) }
    protected open val borderColorWhenSelected: Color get() = Color.web("#2a66ff") //backgroundColor.now.invert().brighter()
    protected open val borderColorWhenNotSelected: Color get() = Color.TRANSPARENT
    protected open val borderColorWhenSameObjectSelected: Color get() = Color.GRAY

    protected val colorPicker: ColorPicker = ColorPicker() styleClass "button"

    val paneY get() = pane.getPaneY(instance.y)

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
        compoundInput<Unit>("Configure loop of object ${instance.obj.name.now}") {
            val periodInput = when (periodUnit) {
                null -> TextField(instance.duration.format(3))
                else -> {
                    val default = (instance.duration / grid!!.getDuration(periodUnit) + 0.95).toInt()
                    Spinner<Int>(1, Int.MAX_VALUE, default)
                }
            } named "Loop period ($periodUnit)"
            val repetitionsInput = Spinner<Int>(1, 1000, 1, 1) named "Number of repetitions"
            if (periodInput is TextField) {
                confirmButton.disableProperty().bind(periodInput.textProperty().map { txt ->
                    val v = txt.toDoubleOrNull()
                    v == null || v == 0.0
                })
            }
            onConfirm {
                val period = when (periodInput) {
                    is Spinner<*> -> periodInput.value as Int * grid!!.getDuration(periodUnit ?: SnapOption.Seconds)
                    is TextField -> periodInput.text.toDouble()
                    else -> error("Invalid period input control: $periodInput")
                }
                val repetitions = repetitionsInput.value
                pane.score.loop(instance, period, repetitions)
            }
        }.showDialog(context)
    }

    open fun initialize(parent: ScorePane) {
        this.pane = parent
        initializeLayout()
        setupSelecting()
        setupDraggingAndResizing(
            parent,
            canUserChangeWidth = true, canUserChangeHeight = true, Tool.Pointer,
            drag = this::dragTo, resize = this::resize,
            startDrag = this::startDrag,
            finishDrag = this::finishedDrag
        )
        setBackground()
        setupCutting()
        setupActions()
        displayKnobs()
        instance.addListener(this)
        instance.obj.addListener(this)
        isInitialized = true
    }

    private fun setupSelecting() {
        addEventHandler(MouseEvent.MOUSE_PRESSED) { ev ->
            toFront()
            context[ScoreObjectSelectionManager].select(this, addToSelection = ev.isShiftDown)
            requestFocus()
            ev.consume()
        }
    }

    private fun initializeLayout() {
        relocate(pane.getX(instance.start), pane.getPaneY(instance.y))
        setPrefSize(getDisplayWidth(), getDisplayHeight())
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
        if (Icon.Mute in supportedActions) {
            muteUnmuteBtn.graphic = if (instance.muted) Icon.Mute.getView() else Icon.Unmute.getView()
            opacity = if (muted) 0.5 else 1.0
        }
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

    override fun changedSpec(parameter: String, newSpec: ControlSpec) {
        envelopeEditors.find { ed -> ed.parameterName == parameter }?.repaint()
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
        val e = EnvelopeEditor(parameter, envelope, this)
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
                val (leftHalf, rightHalf) = instance.cut(cutPosition)
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
        val borderColor = when {
            value -> borderColorWhenSelected
            instance.obj in context[ScoreObjectSelectionManager].selectedObjects -> borderColorWhenSameObjectSelected
            else -> borderColorWhenNotSelected
        }
        border = solidBorder(borderColor, width = BORDER_WIDTH, radius = BORDER_RADIUS)
    }

    override fun isSomeInstanceSelected(yesOrNo: Boolean) {
        val borderColor = when {
            this in context[ScoreObjectSelectionManager].selectedViews -> borderColorWhenSelected
            yesOrNo -> borderColorWhenSameObjectSelected
            else -> borderColorWhenNotSelected
        }
        border = solidBorder(borderColor, width = BORDER_WIDTH, radius = BORDER_RADIUS)
    }

    private fun delete() {
        pane.score.removeObject(instance)
    }

    /*
    * Dragging and resizing
    * */

    protected open fun startDrag(ev: MouseEvent, cursor: Cursor) {
        context[ScoreObjectSelectionManager].select(this, addToSelection = ev.isShiftDown)
        if (cursor.isResizeCursor) {
            val direction = cursor.resizeDirection()
            instance.obj.beginResize(stretch = ev.isShiftDown, direction)
        } else instance.beginMove()
    }

    protected open fun finishedDrag(ev: MouseEvent, cursor: Cursor) {
        if (cursor.isResizeCursor) instance.obj.finishResize()
        else instance.finishMove()
    }

    private fun dragTo(toX: Double, toY: Double) {
        val movedObjects = context[ScoreObjectSelectionManager].selectedViews //one of these is guaranteed to be <this>
        val relativeMinX = movedObjects.minOf { v -> v.layoutX } - this.layoutX // => 0 if only <this> is selected
        val relativeMinY = movedObjects.minOf { v -> v.layoutY } - this.layoutY // => 0 if only <this> is selected
        val relativeMaxX =
            movedObjects.maxOf { v -> v.layoutX + v.width } - this.layoutX // => this.width if only <this> is selected
        val relativeMaxY =
            movedObjects.maxOf { v -> v.layoutY + v.height } - this.layoutY // => this.height if only <this> is selected
        var (x, y) = pane.snapToGrid(toX, toY)
        x = x.coerceAtLeast(-relativeMinX)
        y = y.coerceAtLeast(-relativeMinY)
        if (pane is SubScorePane) x = x.coerceAtMost(pane.width - relativeMaxX)
        y = y.coerceAtMost(pane.height - relativeMaxY)
        val deltaT = pane.getTime(x) - instance.start
        val deltaY = pane.getScoreY(y) - instance.y
        for (view in movedObjects) {
            val inst = view.instance
            inst.moveTo(inst.start + deltaT, inst.y + deltaY, simpleMove = false)
        }
        pane.markX(x)
    }

    protected open fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor) {
        val newDur = pane.getDuration(width)
        val newHeight = pane.getScoreY(height)
        instance.obj.resize(newDur, newHeight)
    }

    open fun getDisplayWidth(): Double = pane.getWidth(instance.duration)

    open fun getDisplayHeight() = pane.getPaneY(instance.height)

    open fun rescale() {
        repaintEnvelopes()
    }

    protected fun repaintEnvelopes() {
        for (e in envelopeEditors) {
            e.repaint()
        }
    }

    final override fun moved(start: Double, y: Double) {
        relocate(pane.getX(instance.start), pane.getPaneY(y))
    }

    override fun resizedObject(obj: ScoreObject) {
        setPrefSize(getDisplayWidth(), getDisplayHeight())
        rescale()
    }

    private fun resize(old: Bounds, deltaX: Double, deltaY: Double, cursor: Cursor, ev: MouseEvent) {
        val direction = cursor.resizeDirection()
        val oldX = if (direction.left) old.minX else old.maxX
        val oldY = if (direction.up) old.minY else old.maxY
        val (x, y) = pane.snapToGrid(oldX + deltaX, oldY + deltaY)
        pane.markX(x)
        val dx = x - oldX
        val dy = y - oldY
        var newWidth = old.width
        if (direction.left) newWidth -= dx
        else if (direction.right) newWidth += dx
        var newHeight = old.height
        if (direction.up) newHeight -= dy
        else if (direction.down) newHeight += dy

        newWidth = newWidth.coerceAtLeast(10.0)
        newHeight = newHeight.coerceAtLeast(10.0)

        if (direction.left) newWidth = newWidth.coerceAtMost(old.maxX)
        if (pane is SubScorePane && direction.right) newWidth = newWidth.coerceAtMost(pane.width - old.minX)
        if (direction.up) newHeight = newHeight.coerceAtMost(old.maxY)
        if (direction.down) newHeight = newHeight.coerceAtMost(pane.height - old.minY)

        resizeObject(newWidth, newHeight, ev, cursor)
    }

    fun getNearestGrid(): TempoGridObjectView? {
        val x = pane.getX(instance.start)
        val y = pane.getScoreY(instance.y)
        return pane.getNearestGrid(x, y)
    }

    fun getDeltaX(direction: HorizontalDirection): Double {
        val grid = getNearestGrid()
        val settings = context[currentProject].settings
        val snapOption = if (settings.snapEnabled.now) settings.snapOption.now else null
        val factor = if (direction == HorizontalDirection.LEFT) -1.0 else 1.0
        val deltaT =
            if (snapOption != null && grid != null) grid.obj.getDuration(snapOption) * factor
            else factor / pane.pixelsPerSecond
        return deltaT
    }

    fun adjustHorizontal(direction: HorizontalDirection, resize: Boolean, stretch: Boolean) {
        val deltaT = getDeltaX(direction)
        if (resize) {
            instance.obj.resize(
                instance.obj.duration + deltaT, instance.obj.height, stretch,
                direction = Direction.horizontal(RIGHT)
            )
        } else {
            val (snappedX, _) = pane.snapToGrid(pane.getX(instance.start + deltaT), paneY)
            instance.setTime(pane.getTime(snappedX))
        }
    }

    open fun adjustVertical(direction: VerticalDirection, resize: Boolean, stretch: Boolean) {
        var deltaY = 0.01
        if (direction == VerticalDirection.UP) deltaY *= -1
        adjustVertical(resize, stretch, deltaY)
    }

    protected fun adjustVertical(resize: Boolean, stretch: Boolean, deltaY: Double) {
        if (resize) {
            instance.obj.resize(
                instance.obj.duration,
                instance.obj.height + deltaY,
                stretch,
                direction = Direction.vertical(VerticalDirection.DOWN)
            )
        } else {
            instance.setY(instance.y + deltaY)
        }
    }

    override fun toString(): String = "ScoreObjectView for ${instance.obj} at ${instance.position}"

    companion object {
        private const val BORDER_WIDTH = 4.0
        private const val BORDER_RADIUS = 2.0
    }
}
