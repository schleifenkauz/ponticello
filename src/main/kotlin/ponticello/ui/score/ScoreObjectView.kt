package ponticello.ui.score

import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.ContextualizedAction
import fxutils.actions.registerShortcuts
import fxutils.prompt.DetailPane
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.context.compoundEdit
import hextant.context.withoutUndo
import javafx.beans.InvalidationListener
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Bounds
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.VerticalDirection
import javafx.scene.Cursor
import javafx.scene.control.ColorPicker
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.paint.Color
import javafx.scene.paint.Color.BLACK
import ponticello.impl.*
import ponticello.model.obj.project
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.project.UIState
import ponticello.model.project.settings
import ponticello.model.registry.reference
import ponticello.model.score.*
import ponticello.model.score.Score.Companion.rootScore
import ponticello.ui.actions.ObjectActionContext
import ponticello.ui.actions.ObjectActions
import ponticello.ui.controls.InlineParameterControlsBar
import ponticello.ui.controls.NameControl
import ponticello.ui.dock.AppLayout
import ponticello.ui.impl.resizeMode
import ponticello.ui.impl.setupDraggingAndResizing
import ponticello.ui.launcher.PonticelloMainActivity
import ponticello.ui.launcher.ScoreObjectDetailPane
import ponticello.ui.registry.ScoreObjectRegistryPane
import reaktive.value.*
import reaktive.value.binding.*
import reaktive.value.fx.asObservableValue
import reaktive.value.fx.asReactiveValue

abstract class ScoreObjectView(
    val instance: ScoreObjectInstance,
) : Pane(), ScoreObjectInstance.Listener, ScoreObject.Listener, TimeBlock {
    abstract val obj: ScoreObject

    var isInitialized: Boolean = false
        private set
    lateinit var parentPane: ScorePane
        private set

    protected val isSubWindow: Boolean get() = scene.window != context[PonticelloMainActivity].primaryStage

    lateinit var context: Context
        private set

    private var isCreatingLoop = false
    private val loopedObjects = mutableListOf<ScoreObjectInstance>()

    protected open val defaultBackgroundColor: ReactiveValue<Color>
        get() = reactiveVariable(BLACK)
    val backgroundColor by lazy { obj.associatedColor.orElse(defaultBackgroundColor) }
    protected open val borderColorWhenSelected: Color get() = Color.web("#2a66ff")
    protected open val borderColorWhenNotSelected: Color get() = Color.TRANSPARENT
    protected open val borderColorWhenSameObjectSelected: Color get() = Color.GRAY

    protected val inlineControls = HBox() styleClass "score-object-top-bar"
    lateinit var inlineNameControl: NameControl
        private set
    lateinit var inlineActionBar: ActionBar
        private set

    protected val colorPicker: ColorPicker = ColorPicker() styleClass "button"

    override val absolutePosition
        get() = instance.position + parentPane.absolutePosition

    init {
        styleClass("score-object")
        isFocusTraversable = true
        colorPicker.setFixedWidth(30.0)
        colorPicker.prefHeight = 30.0
    }

    override fun getDuration(width: Double): Decimal = parentPane.getDuration(width) ////TODO could be computed locally

    override fun getWidth(duration: Decimal): Double = parentPane.getWidth(duration) //TODO could be computed locally

    override fun getTime(x: Double): Decimal = getDuration(x) //TODO is this right?

    override fun getX(time: Decimal): Double = getWidth(time) //TODO is this right?

    fun getScoreY(screenY: Double): Decimal = parentPane.getScoreY(screenY)

    fun getScreenY(scoreY: Decimal): Double = parentPane.getScreenY(scoreY)

    fun getDetailPane(extraActions: List<ContextualizedAction> = emptyList()): DetailPane {
        val detailPane = DetailPane(labelWidth = 100.0)
        if (obj is ScoreObject.Unresolved) {
            return detailPane
        } else {
            val ctx = ObjectActionContext.SingleObjectContext(this)
            val headerBox = HBox(
                5.0,
                NameControl(obj).setFixedWidth(150.0),
                ActionBar(ObjectActions.multiObjectActions.withContext(ctx), buttonStyle = "medium-icon-button"),
                ActionBar(ObjectActions.singleObjectActions.withContext(ctx), buttonStyle = "medium-icon-button"),
                infiniteSpace(),
                ActionBar(extraActions, buttonStyle = "medium-icon-button"),
            ).centerChildren().pad(8.0)
            detailPane.children.add(headerBox)
            detailPane.registerShortcuts(ObjectActions.all.withContext(ctx))
            if (obj.canResize) {
                val durationLabel = label(obj.duration().map { dur ->
                    "${dur.round(2).toCanonicalString()} seconds"
                }).pad(5.0)
                detailPane.addItem("Duration", durationLabel)
            }
            setupDetailPane(detailPane)
            return detailPane
        }
    }

    protected open fun setupDetailPane(pane: DetailPane) {}

    protected open fun initialize() {
        initializeLayout()
        setupColorPicker()
        instance.addListener(this)
        obj.addListener(this)
        isInitialized = true
        inlineNameControl = NameControl(obj).autoSize()
        val controlsDisplay = context[UIState].controlsDisplay
        inlineControls.prefWidthProperty().bind(prefWidthProperty())
        inlineControls.backgroundProperty().bind(
            Bindings.`when`(
                Bindings.equal(controlsDisplay, SimpleObjectProperty(InlineControlsDisplay.CONTROLS_BAR))
            ).then(background(Color.web("#1d1d20"))).otherwise(background(Color.gray(0.3, 0.35)))
        )
        inlineControls.children.add(inlineNameControl)
        inlineControls.children.add(infiniteSpace())
        val actions = ObjectActions.multiObjectActions.withContext(ObjectActionContext.SingleObjectContext(this))
        inlineActionBar = ActionBar(actions, buttonStyle = "small-icon-button")
        inlineControls.children.add(inlineActionBar)
        inlineControls.visibleProperty().bind(
            inlineControlsVisibilityCondition(controlsDisplay)
                .asObservableValue()
        )
        inlineControls.children.addListener(InvalidationListener { _ ->
            updateInlineControlsVisibility()
        })
        children.add(inlineControls)
    }

    protected open fun inlineControlsVisibilityCondition(
        controlsDisplay: ReactiveVariable<InlineControlsDisplay>,
    ): ReactiveBoolean = controlsDisplay.notEqualTo(InlineControlsDisplay.NONE)
        .and(instance.hideInlineControls.not())
        .and(inlineNameControl.visibleProperty().asReactiveValue())
//            .and(inlineControls.widthProperty().lessThanOrEqualTo(prefWidthProperty()).asReactiveValue())

    fun initialize(parent: ScorePane) {
        if (isInitialized) return
        parentPane = parent
        context = parent.context
        initialize()
        if (!parent.isRoot(obj)) {
            val canResize = obj.canResize
            setupDraggingAndResizing(
                parent.context,
                canUserChangeWidth = canResize, canUserChangeHeight = canResize,
                resizeDescription = { ev -> if (ev.isAltDown) "Create loop" else "Resize object" },
                drag = this::dragTo, resize = this::resize,
                startDrag = this::startDrag, finishDrag = this::finishedDrag
            )
            addMouseActions()
            val tooltip = Tooltip()
            tooltip.textProperty().bind(obj.name.asObservableValue())
            Tooltip.install(this, tooltip)
        }
    }

    fun selectView(addToSelection: Boolean) {
        toFront()
        context[ScoreObjectSelectionManager].select(this, addToSelection = addToSelection)
        requestFocus()
    }

    private fun initializeLayout() {
        if (!parentPane.isRoot(obj)) {
            relocate(parentPane.getX(instance.start), parentPane.getScreenY(instance.y))
        }
        setPrefSize(getDisplayWidth(), getDisplayHeight())
    }

    private fun setupColorPicker() {
        colorPicker.userData = backgroundColor.forEach { color ->
            colorPicker.value = color
        }
        colorPicker.valueProperty().addListener { _, oldColor, newColor ->
            obj.associatedColor.now = newColor
            context[UndoManager].record(ScoreObjectEdit.Recolor(obj, oldColor, newColor))
        }
    }

    override fun toggledMute(muted: Boolean) {
        opacity = if (muted) 0.5 else 1.0
    }

    private fun addMouseActions() {
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            when {
                ev.button == MouseButton.SECONDARY && ev.modifiers.isEmpty() -> {
                    val toolPane = context[ScoreObjectDetailPane]
                    toolPane.showDetailPane(this)
                    toolPane.setShowing(true)
                }

                ev.button == MouseButton.SECONDARY && ev.modifiers == setOf(Shift) -> {
                    if (!parentPane.isRoot(obj)) {
                        context[AppLayout].get<ScoreObjectRegistryPane>().showContent(obj)
                    }
                }

                ev.button == MouseButton.PRIMARY -> {
                    selectView(addToSelection = ev.isShiftDown)
                }

            }
            ev.consume()
        }
    }

    private fun cutObject(ev: MouseEvent) {
        if (obj is ScoreObjectGroup && ev.isShiftDown) {
            val position = getScoreY(ev.y)
            val (top, bottom) = (obj as ScoreObjectGroup).cutVertically(position)
            replaceWithCutHalves(top, bottom, relativePosition = ObjectPosition(zero, position))
        } else if (!ev.isShiftDown) {
            val position = getDuration(ev.x)
            val leftHalf = obj.cut(position, LEFT, "${obj.name.now}_left") ?: return
            val rightHalf = obj.cut(position, RIGHT, "${obj.name.now}_right") ?: return
            replaceWithCutHalves(leftHalf, rightHalf, relativePosition = ObjectPosition(position, zero))
        }
    }

    fun inferQuantization(): Boolean {
        val position = instance.position
        val (gridStart, meter) = parentPane.getNearestGrid(position) ?: return false
        obj.liveConfig.yPosition.set(absolutePosition.y)
        obj.quantizationConfig.meter.set(meter.reference())
        val (durUnit, durValue) = meter.represent(obj.duration)
        obj.quantizationConfig.durationUnit.set(durUnit)
        obj.quantizationConfig.durationValue.set(durValue)
        var delta = absolutePosition.time - gridStart
        while (delta < zero) delta += obj.duration
        while (delta > obj.duration) delta -= obj.duration
        val (offsetUnit, offsetValue) = meter.represent(delta)
        obj.quantizationConfig.offsetUnit.set(offsetUnit)
        obj.quantizationConfig.offsetValue.set(offsetValue)
        return true
    }

    private fun replaceWithCutHalves(half1: ScoreObject, half2: ScoreObject, relativePosition: ObjectPosition) {
        context.compoundEdit("Cut object") {
            for (inst in context[rootScore].instancesOf(obj).toList()) {
                val score = inst.score
                score!!.removeObject(inst, Score.RegistryOption.KEEP_IN_REGISTRY)
                val inst1 = ScoreObjectInstance(half1, inst.position, inst.muted.copy())
                val inst2 = ScoreObjectInstance(half2, inst.position + relativePosition, inst.muted.copy())
                score.addObject(inst1, autoSelect = false)
                score.addObject(inst2, autoSelect = true)
            }
        }
    }

    fun setSelected(value: Boolean) {
        val (borderColor, width) = when {
            value -> borderColorWhenSelected to BORDER_WIDTH
            obj in context[ScoreObjectSelectionManager].selectedObjects -> borderColorWhenSameObjectSelected to BORDER_WIDTH
            else -> borderColorWhenNotSelected to 0.5
        }
        border = solidBorder(borderColor, width = width, radius = BORDER_RADIUS)
        if (!value) {
            context[ScoreObjectDetailPane].hideDetailPane(this)
        }
    }

    override fun isSomeInstanceSelected(yesOrNo: Boolean) {
        val (borderColor, width) = when {
            instance in context[ScoreObjectSelectionManager].selectedInstances -> borderColorWhenSelected to BORDER_WIDTH
            yesOrNo -> borderColorWhenSameObjectSelected to BORDER_WIDTH
            else -> borderColorWhenNotSelected to 0.5
        }
        border = solidBorder(borderColor, width = width, radius = BORDER_RADIUS)
    }

    /*
    * Dragging and resizing
    * */

    protected open fun startDrag(ev: MouseEvent, cursor: Cursor): Boolean {
        if (ev.target is Label) return false
        val selectionManager = context[ScoreObjectSelectionManager]
        if (!selectionManager.isSelected(this)) {
            selectView(addToSelection = ev.isShiftDown)
        }
        if (cursor.isResizeCursor) {
            selectionManager.deselectAll()
            if (ev.isAltDown) {
                isCreatingLoop = true
                return true
            } else {
                val direction = cursor.resizeDirection()
                return obj.beginResize(ev.resizeMode ?: return false, direction)
            }
        } else {
            val selectedInstances = selectionManager.selectedInstances + this.instance
            for (inst in selectedInstances) {
                inst.beginMove()
            }
            return true
        }
    }

    protected open fun finishedDrag(ev: MouseEvent, cursor: Cursor) {
        if (isCreatingLoop) return finishLoop()
        if (cursor.isResizeCursor) {
            obj.finishResize()
        } else {
            val selectedInstances = context[ScoreObjectSelectionManager].selectedInstances + this.instance
            for (inst in selectedInstances) {
                inst.finishMove()
            }
        }
    }

    private fun finishLoop() {
        isCreatingLoop = false
        if (loopedObjects.isEmpty()) return
        context.compoundEdit("Create loop") {
            for (inst in loopedObjects) {
                context[UndoManager].record(ScoreEdit.AddObject(inst, parentPane.score))
            }
        }
        loopedObjects.clear()
    }

    private fun dragTo(toX: Double, toY: Double) {
        context[ScoreObjectDetailPane].hideCurrentlyShown()
        val movedObjects = context[ScoreObjectSelectionManager].selectedInstances + this.instance
        val minDeltaT = -movedObjects.minOf { inst -> inst.start }
        val maxDeltaT = movedObjects.minOf { inst -> inst.score!!.maxTime.now - inst.end }
        val minDeltaY = -movedObjects.minOf { inst -> inst.y }
        val maxDeltaY = movedObjects.minOf { inst -> inst.score!!.maxY.now - (inst.y + inst.height) }
        val (t, y) = parentPane.snapToGrid(toX, toY)
        val deltaT = (t - instance.start).coerceIn(minDeltaT..maxDeltaT)
        val deltaY = (y - instance.y).coerceIn(minDeltaY..maxDeltaY)
        for (inst in movedObjects) {
            inst.moveTo(inst.start + deltaT, inst.y + deltaY, simpleMove = false)
        }
        parentPane.markT(instance.start + deltaT)
    }

    open fun getDisplayWidth(): Double = getWidth(obj.duration)

    open fun getDisplayHeight() = getScreenY(obj.height)

    open fun rescale() {
        updateInlineControlsVisibility()
    }

    fun updateInlineControlsVisibility() {
        updateInlineControlsVisibility(inlineControls, prefWidth)
    }

    private fun updateInlineControlsVisibility(box: HBox, availableWidth: Double) {
        var usedWidth = 0.0
        for (child in box.children) {
            val width = child.prefWidth(-1.0)
            if (child is InlineParameterControlsBar) {
                if (!child.isVisible) continue
                updateInlineControlsVisibility(child, availableWidth - usedWidth)
                usedWidth += width
            } else {
                if (child.visibleProperty().isBound) {
                    System.err.println("Warning: bound visibility of $child, (child of $box)")
                    continue
                }
                if (usedWidth + width <= availableWidth) {
                    child.isVisible = true
                    child.isManaged = true
                    usedWidth += width
                } else {
                    child.isVisible = false
                    child.isManaged = false
                }
            }
        }
    }

    final override fun moved(start: Decimal, y: Decimal) {
        if (!parentPane.isRoot(obj)) {
            relocate(parentPane.getX(instance.start), parentPane.getScreenY(y))
        }
    }

    override fun resizedObject(obj: ScoreObject) {
        if (!parentPane.isRoot(obj)) {
            setPrefSize(getDisplayWidth(), getDisplayHeight())
        }
        rescale()
    }

    @Suppress("UNUSED_PARAMETER") //parameter [ev] is needed to be compatible with Node.setupDraggingAndResizing
    private fun resize(old: Bounds, deltaX: Double, deltaY: Double, cursor: Cursor, ev: MouseEvent) {
        check(obj.canResize) { "Attempt to resize object that is not resizable" }
        context[ScoreObjectDetailPane].hideCurrentlyShown()
        val parentPane = parentPane
        val direction = cursor.resizeDirection()
        val oldX = if (direction.left) old.minX else old.maxX
        val oldY = if (direction.up) old.minY else old.maxY
        val (t, y) = parentPane.snapToGrid(oldX + deltaX, oldY + deltaY)
        parentPane.markT(t)
        val dt = t - parentPane.getTime(oldX)
        val dy = y - parentPane.getScoreY(oldY)
        var newDur = getDuration(old.width)
        if (direction.left) newDur -= dt
        else if (direction.right) newDur += dt
        var newHeight = parentPane.getScoreY(old.height)
        if (direction.up) newHeight -= dy
        else if (direction.down) newHeight += dy

        newDur = newDur.coerceAtLeast(getDuration(10.0))
        newHeight = newHeight.coerceAtLeast(0.01.asY)

        if (direction.left) newDur = newDur.coerceAtMost(instance.end)
        if (parentPane is SubScorePane && direction.right) newDur =
            newDur.coerceAtMost(parentPane.score.maxTime.now - instance.start)
        if (direction.up) newHeight = newHeight.coerceAtMost(instance.y + instance.height)
        if (direction.down) {
            val parentHeight = parentPane.associatedObject?.height ?: 1.0.asY
            newHeight = newHeight.coerceAtMost(parentHeight - instance.y)
        }
        if (isCreatingLoop) {
            if (direction.up || direction.down) return
            val factor = if (direction.left) -1 else if (direction.right) +1 else return
            val loopCount = (newDur / obj.duration).roundToInt() - 1
            if (loopCount < 0) return
            if (loopCount > loopedObjects.size) {
                for (i in loopedObjects.size + 1..loopCount) {
                    val offset = ObjectPosition(time = i * obj.duration * factor, y = zero)
                    val obj = ScoreObjectInstance(obj, instance.position + offset, instance.muted.copy())
                    loopedObjects.add(obj)
                    context.withoutUndo {
                        parentPane.score.addObject(obj, autoSelect = false)
                    }
                }
            } else {
                for (i in loopedObjects.size - 1 downTo loopCount) {
                    val inst = loopedObjects.removeAt(i)
                    context.withoutUndo {
                        parentPane.score.removeObject(inst, Score.RegistryOption.KEEP_IN_REGISTRY)
                    }
                }
            }
        } else {
            obj.resize(newDur, newHeight)
        }
    }

    fun getDeltaT(direction: HorizontalDirection): Decimal {
        val parentPane = parentPane
        val factor = if (direction == LEFT) -1.0 else 1.0
        val meter = parentPane.getNearestGrid(instance.position)?.second
        val settings = context.project.settings
        val snapOption = if (settings.snapEnabled.now) settings.snapOption.now else null
        val deltaT =
            if (snapOption != null && meter != null) meter.getDuration(snapOption) * factor
            else getDuration(factor)
        return deltaT
    }

    fun adjustHorizontal(direction: HorizontalDirection, resize: Boolean, resizeMode: ScoreObject.ResizeMode?) {
        check(obj.canResize) { "Cannot adjust horizontal $this because it has its own sub-window." }
        val parentPane = parentPane
        val deltaT = getDeltaT(direction)
        if (resize) {
            val targetDuration = (obj.duration + deltaT).coerceAtMost(parentPane.score.maxTime.now - instance.start)
            obj.resize(
                targetDuration, obj.height,
                resizeMode!!, Direction.horizontal(RIGHT)
            )
            parentPane.markT(instance.end)
        } else {
            val (t, _) = parentPane.snapToGrid(instance.position.plusTime(deltaT))
            val start = t.coerceIn(zero, parentPane.score.maxTime.now - obj.duration)
            instance.setTime(start)
            parentPane.markT(start)
        }
    }

    open fun adjustVertical(direction: VerticalDirection, resize: Boolean, resizeMode: ScoreObject.ResizeMode) {
        var deltaY = 0.01.asTime
        if (direction == VerticalDirection.UP) deltaY *= -1
        adjustVertical(resize, resizeMode, deltaY)
    }

    protected fun adjustVertical(resize: Boolean, resizeMode: ScoreObject.ResizeMode, deltaY: Decimal) {
        val parentPane = parentPane
        if (resize) {
            val targetHeight = (obj.height + deltaY).coerceAtMost(parentPane.score.maxY.now - instance.y)
            obj.resize(
                obj.duration, targetHeight,
                resizeMode, Direction.vertical(VerticalDirection.DOWN)
            )
        } else {
            val y = (instance.y + deltaY).coerceIn(zero, parentPane.score.maxY.now - obj.height)
            instance.setY(y)
        }
    }

    override fun toString(): String = "ScoreObjectView for $instance"

    companion object {
        private const val BORDER_WIDTH = 3.0
        const val BORDER_RADIUS = 2.0
    }
}
