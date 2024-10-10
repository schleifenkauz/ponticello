package xenakis.ui

import hextant.context.Context
import hextant.undo.UndoManager
import hextant.undo.compoundEdit
import javafx.geometry.Bounds
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
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
import xenakis.impl.*
import xenakis.model.*
import xenakis.model.InteractionSettings.SnapOption
import xenakis.model.Score.Companion.rootScore
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.ui.ToolSelector.Tool
import xenakis.ui.XenakisController.Companion.currentProject
import xenakis.ui.prompt.compoundInput

abstract class ScoreObjectView(
    val instance: ScoreObjectInstance
) : Pane(), ScoreObjectInstance.Listener, SynthControls.View, ScoreObject.Listener, TimeBlock {
    var isInitialized: Boolean = false
        private set
    lateinit var pane: ScorePane
        private set
    val context: Context get() = pane.context

    private val nameEditor: NameControl = NameControl(instance.obj)
    private lateinit var muteUnmuteBtn: Button
    private val envelopeDisplayObservers = mutableMapOf<String, Observer>()
    val actions = HBox().centerChildren() styleClass "actions"
    private val knobControls = FlowPane().centerChildren() styleClass "knobs" //TODO remove

    private val envelopeEditors = mutableListOf<EnvelopeEditor>()

    protected open val defaultBackgroundColor: ReactiveValue<Color>
        get() = reactiveVariable(BLACK)
    val backgroundColor by lazy { instance.obj.associatedColor.orElse(defaultBackgroundColor) }
    protected open val borderColorWhenSelected: Color get() = Color.web("#2a66ff")
    protected open val borderColorWhenNotSelected: Color get() = Color.TRANSPARENT
    protected open val borderColorWhenSameObjectSelected: Color get() = Color.GRAY

    protected val colorPicker: ColorPicker = ColorPicker() styleClass "button"

    override val absolutePosition get() = instance.position + pane.absolutePosition

    init {
        styleClass("score-object")
        isFocusTraversable = true
        colorPicker.setFixedWidth(30.0)
        colorPicker.prefHeight = 30.0
    }

    override fun getDuration(width: Double): Decimal = pane.getDuration(width)

    override fun getWidth(duration: Decimal): Double = pane.getWidth(duration)

    override fun getTime(x: Double): Decimal = getDuration(x)

    override fun getX(time: Decimal): Double = getWidth(time)

    protected open val supportedActions get() = listOf(Icon.Delete, Icon.Mute, Icon.Repeat)

    fun getDetailPane() = DetailPane().apply {
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
        val grid = pane.getNearestGrid(instance.position)?.obj
            .takeIf { settings.snapEnabled.now } as TempoGridObject?
        val periodUnit = if (grid == null) null else settings.snapOption.now
        compoundInput<Unit>("Configure loop of object ${instance.obj.name.now}") {
            val periodInput = when (periodUnit) {
                null -> TextField(instance.duration.toString())
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
                    is TextField -> periodInput.text.parseDecimal()!!
                    else -> error("Invalid period input control: $periodInput")
                }
                val repetitions = repetitionsInput.value
                pane.score.loop(instance, period, repetitions)
            }
        }.showDialog(context, anchorNode = this)
    }

    open fun initialize(parent: ScorePane) {
        if (isInitialized) return
        this.pane = parent
        initializeLayout()
        val canResize = instance.obj.canResize
        setupDraggingAndResizing(
            parent,
            canUserChangeWidth = canResize, canUserChangeHeight = canResize, Tool.Pointer,
            drag = this::dragTo, resize = this::resize,
            startDrag = this::startDrag,
            finishDrag = this::finishedDrag
        )
        setBackground()
        addMouseActions()
        setupActions()
        displayKnobs()
        instance.addListener(this)
        instance.obj.addListener(this)
        isInitialized = true
    }

    protected fun selectThis(addToSelection: Boolean) {
        toFront()
        context[ScoreObjectSelectionManager].select(this, addToSelection = addToSelection)
        requestFocus()
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
        val e = EnvelopeEditor(parameter, envelope, this, pane = this)
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

    private fun addMouseActions() {
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            ev.consume()
            val tool = context[XenakisUI].toolSelector.selected.value
            when (tool) {
                Tool.Cut -> {
                    val obj = instance.obj
                    if (obj is ScoreObjectGroup && ev.isShiftDown) {
                        val position = pane.getScoreY(ev.y)
                        val (top, bottom) = obj.cutVertically(position)
                        replaceWithCutHalves(top, bottom, relativePosition = ObjectPosition(zero, position))
                    } else if (!ev.isShiftDown) {
                        val position = pane.getDuration(ev.x)
                        val leftHalf = obj.cut(position, LEFT, "${obj.name.now}_left") ?: return@addEventHandler
                        val rightHalf = obj.cut(position, RIGHT, "${obj.name.now}_right") ?: return@addEventHandler
                        replaceWithCutHalves(leftHalf, rightHalf, relativePosition = ObjectPosition(position, zero))
                    }
                }

                else -> {
                    val playback = context[PlaybackManager]
                    if (ev.isControlDown) {
                        playback.attachToView(this)
                    }
                    if (playback.isAttachedTo(this)) {
                        playback.playHead.setPlayHeadX(ev.x)
                    }
                }
            }
        }
        addEventHandler(MouseEvent.MOUSE_PRESSED) { ev ->
            selectThis(ev.isShiftDown)
            ev.consume()
        }
    }

    private fun replaceWithCutHalves(half1: ScoreObject, half2: ScoreObject, relativePosition: ObjectPosition) {
        context.compoundEdit("Cut object") {
            for (inst in context[rootScore].instancesOf(instance.obj).toList()) {
                val score = inst.score
                score!!.removeObject(inst)
                val inst1 = ScoreObjectInstance(half1, inst.position, inst.muted)
                val inst2 = ScoreObjectInstance(half2, inst.position + relativePosition, inst.muted)
                score.addObject(inst1)
                score.addObject(inst2)
            }
        }
    }

    fun setSelected(value: Boolean) {
        val (borderColor, width) = when {
            value -> borderColorWhenSelected to BORDER_WIDTH
            instance.obj in context[ScoreObjectSelectionManager].selectedObjects -> borderColorWhenSameObjectSelected to BORDER_WIDTH
            else -> borderColorWhenNotSelected to 0.5
        }
        border = solidBorder(borderColor, width = width, radius = BORDER_RADIUS)
    }

    override fun isSomeInstanceSelected(yesOrNo: Boolean) {
        val (borderColor, width) = when {
            this in context[ScoreObjectSelectionManager].selectedViews -> borderColorWhenSelected to BORDER_WIDTH
            yesOrNo -> borderColorWhenSameObjectSelected to BORDER_WIDTH
            else -> borderColorWhenNotSelected to 0.5
        }
        border = solidBorder(borderColor, width = width, radius = BORDER_RADIUS)
    }

    private fun delete() {
        pane.score.removeObject(instance)
    }

    /*
    * Dragging and resizing
    * */

    protected open fun startDrag(ev: MouseEvent, cursor: Cursor): Boolean {
        if (cursor.isResizeCursor) {
            val direction = cursor.resizeDirection()
            return instance.obj.beginResize(ev.resizeType ?: return false, direction)
        } else {
            instance.beginMove()
            return true
        }
    }

    protected open fun finishedDrag(ev: MouseEvent, cursor: Cursor) {
        if (cursor.isResizeCursor) instance.obj.finishResize()
        else instance.finishMove()
    }

    private fun dragTo(toX: Double, toY: Double) {
        val movedObjects =
            context[ScoreObjectSelectionManager].selectedInstances //one of these is guaranteed to be <this.instance>
        val relativeMinT = movedObjects.minOfOrNull { inst ->
            inst.start - this.instance.start
        } ?: 0.0.asTime
        val relativeMinY = movedObjects.minOfOrNull { inst ->
            inst.y - this.instance.y
        } ?: 0.0.asY
        val relativeMaxT = movedObjects.maxOfOrNull { inst ->
            inst.start + inst.obj.duration - this.instance.start
        } ?: instance.obj.duration
        val relativeMaxY = movedObjects.maxOfOrNull { inst ->
            inst.y + inst.height - this.instance.y
        } ?: instance.obj.height
        var (t, y) = pane.snapToGrid(toX, toY)
        t = t.coerceAtLeast(-relativeMinT)
        y = y.coerceAtLeast(-relativeMinY)
        if (pane is SubScorePane) t = t.coerceAtMost(instance.obj.duration - relativeMaxT)
        y = y.coerceAtMost(pane.height - relativeMaxY)
        val deltaT = t - instance.start
        val deltaY = y - instance.y
        for (inst in movedObjects) {
            inst.moveTo(inst.start + deltaT, inst.y + deltaY, simpleMove = false)
        }
        pane.markT(t)
    }

    open fun getDisplayWidth(): Double = pane.getWidth(instance.duration)

    open fun getDisplayHeight() = pane.getPaneY(instance.height)

    open fun rescale() {
        repaintEnvelopes()
    }

    private fun repaintEnvelopes() {
        for (e in envelopeEditors) {
            e.repaint()
        }
    }

    final override fun moved(start: Decimal, y: Decimal) {
        relocate(pane.getX(instance.start), pane.getPaneY(y))
    }

    override fun resizedObject(obj: ScoreObject) {
        setPrefSize(getDisplayWidth(), getDisplayHeight())
        rescale()
    }

    private fun resize(old: Bounds, deltaX: Double, deltaY: Double, cursor: Cursor, ev: MouseEvent) {
        check(instance.obj.canResize) { "Attempt to resize object that is not resizable" }
        val direction = cursor.resizeDirection()
        val oldX = if (direction.left) old.minX else old.maxX
        val oldY = if (direction.up) old.minY else old.maxY
        val (t, y) = pane.snapToGrid(oldX + deltaX, oldY + deltaY)
        pane.markT(t)
        val dt = t - getDuration(oldX)
        val dy = y - pane.getScoreY(oldY)
        var newDur = getDuration(old.width)
        if (direction.left) newDur -= dt
        else if (direction.right) newDur += dt
        var newHeight = pane.getScoreY(old.height)
        if (direction.up) newHeight -= dy
        else if (direction.down) newHeight += dy

        newDur = newDur.coerceAtLeast(getDuration(10.0))
        newHeight = newHeight.coerceAtLeast(0.01.asY)

        if (direction.left) newDur = newDur.coerceAtMost(getDuration(old.maxX))
        if (pane is SubScorePane && direction.right) newDur =
            newDur.coerceAtMost(instance.duration - getDuration(old.minX))
        if (direction.up) newHeight = newHeight.coerceAtMost(pane.getScoreY(old.maxY))
        if (direction.down) newHeight = newHeight.coerceAtMost(instance.height - pane.getScoreY(old.minY))

        instance.obj.resize(newDur, newHeight)
    }

    fun getDeltaX(direction: HorizontalDirection): Decimal {
        val grid = pane.getNearestGrid(instance.position)?.obj as TempoGridObject?
        val settings = context[currentProject].settings
        val snapOption = if (settings.snapEnabled.now) settings.snapOption.now else null
        val factor = if (direction == LEFT) -1.0 else 1.0
        val deltaT =
            if (snapOption != null && grid != null) grid.getDuration(snapOption) * factor
            else (factor / pane.pixelsPerSecond).asTime
        return deltaT
    }

    fun adjustHorizontal(direction: HorizontalDirection, resize: Boolean, resizeType: ScoreObject.ResizeType?) {
        val deltaT = getDeltaX(direction)
        if (resize) {
            val targetDuration = (instance.obj.duration + deltaT).coerceAtMost(pane.maxTime - instance.start)
            instance.obj.resize(
                targetDuration, instance.obj.height,
                resizeType!!, Direction.horizontal(RIGHT)
            )
        } else {
            val (t, _) = pane.snapToGrid(instance.position.plusTime(deltaT))
            val start = t.coerceIn(zero, pane.maxTime - instance.obj.duration)
            instance.setTime(start)
        }
    }

    open fun adjustVertical(direction: VerticalDirection, resize: Boolean, resizeType: ScoreObject.ResizeType) {
        var deltaY = 0.01.asTime
        if (direction == VerticalDirection.UP) deltaY *= -1
        adjustVertical(resize, resizeType, deltaY)
    }

    protected fun adjustVertical(resize: Boolean, resizeType: ScoreObject.ResizeType, deltaY: Decimal) {
        if (resize) {
            val targetHeight = (instance.obj.height + deltaY).coerceAtMost(pane.maxY - instance.y)
            instance.obj.resize(
                instance.obj.duration, targetHeight,
                resizeType, Direction.vertical(VerticalDirection.DOWN)
            )
        } else {
            val y = (instance.y + deltaY).coerceIn(zero, pane.maxY - instance.obj.height)
            instance.setY(y)
        }
    }

    override fun toString(): String = "ScoreObjectView for $instance"

    companion object {
        private const val BORDER_WIDTH = 3.0
        private const val BORDER_RADIUS = 2.0
    }
}
