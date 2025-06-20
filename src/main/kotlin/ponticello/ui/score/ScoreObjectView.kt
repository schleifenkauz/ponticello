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
import javafx.beans.value.ObservableValue
import javafx.geometry.*
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.control.ColorPicker
import javafx.scene.control.Label
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Background
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Region
import javafx.scene.paint.Color
import javafx.scene.paint.Color.BLACK
import javafx.scene.robot.Robot
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
import ponticello.ui.impl.Cursors
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
    lateinit var inlineNameLabel: Label
        private set
    private lateinit var inlineActionBar: ActionBar

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
        setupColorPicker()
        instance.addListener(this)
        obj.addListener(this)
        if (!parentPane.isRoot(obj)) {
            setupInlineControls()
        }
        isInitialized = true
    }

    private fun setupInlineControls() {
        inlineNameLabel = label(obj.name)
        val controlsDisplay = context[UIState].controlsDisplay
        inlineControls.prefWidthProperty().bind(prefWidthProperty())
        inlineControls.backgroundProperty().bind(
            inlineControlsBackground(controlsDisplay)
        )
        inlineControls.children.add(inlineNameLabel)
        inlineControls.children.add(infiniteSpace())
        val actions = ObjectActions.multiObjectActions.withContext(ObjectActionContext.SingleObjectContext(this))
        inlineActionBar = ActionBar(actions, buttonStyle = "small-icon-button")
        inlineActionBar.cursor = Cursor.DEFAULT
        inlineControls.children.add(inlineActionBar)
        inlineControls.visibleProperty().bind(inlineControlsVisibilityCondition(controlsDisplay))
        inlineControls.children.addListener(InvalidationListener { _ ->
            updateInlineControlsVisibility()
        })
        children.add(inlineControls)
    }

    protected open fun inlineControlsBackground(
        controlsDisplay: ReactiveVariable<InlineControlsDisplay>,
    ): ObservableValue<Background> = Bindings.`when`(
        Bindings.equal(controlsDisplay, SimpleObjectProperty(InlineControlsDisplay.CONTROLS_BAR))
    ).then(background(Color.web("#1d1d20"))).otherwise(background(Color.gray(0.1, 0.5)))

    protected open fun inlineControlsVisibilityCondition(
        controlsDisplay: ReactiveVariable<InlineControlsDisplay>,
    ): ObservableValue<Boolean> = controlsDisplay.notEqualTo(InlineControlsDisplay.NONE)
        .and(instance.hideInlineControls.not())
        .and(inlineNameLabel.visibleProperty().asReactiveValue())
        .asObservableValue()

    protected open fun dragTargets(): List<Node> = listOf(this)

    fun initialize(parent: ScorePane) {
        if (isInitialized) return
        parentPane = parent
        context = parent.context
        initialize()
        if (!parent.isRoot(obj)) {
            if (obj.canResize) {
                setupResizingRegions()
            }
            setupDragging()
            addMouseActions()
        }
    }

    fun selectView(addToSelection: Boolean) {
        toFront()
        context[ScoreObjectSelectionManager].select(this, addToSelection = addToSelection)
        requestFocus()
    }

    private fun setupDragging() {
        var dragStart: Point2D? = null
        var oldBounds: Bounds? = null
        for (dragTarget in dragTargets()) {
            dragTarget.cursor = Cursors.OPEN_HAND
            dragTarget.addEventHandler(MouseEvent.ANY) { ev ->
                when (ev.eventType) {
                    MouseEvent.DRAG_DETECTED -> {
                        dragTarget.cursor = Cursors.CLOSED_HAND
                        dragStart = Point2D(ev.screenX, ev.screenY)
                        oldBounds = BoundingBox(layoutX, layoutY, width, height)
                        dragTarget.startFullDrag()
                        startMove(ev)
                        ev.consume()
                    }

                    MouseEvent.MOUSE_DRAGGED -> {
                        val start = dragStart
                        if (start != null) {
                            val deltaX = ev.screenX - start.x
                            val deltaY = ev.screenY - start.y
                            val x = oldBounds!!.minX + deltaX
                            val y = oldBounds!!.minY + deltaY
                            dragTo(x, y, ev)
                            ev.consume()
                        }
                    }

                    MouseEvent.MOUSE_RELEASED -> {
                        if (dragStart != null) {
                            dragTarget.cursor = Cursors.OPEN_HAND
                            instance.finishMove()
                            dragStart = null
                            ev.consume()
                        }
                    }
                }
            }
        }
    }

    private fun setupResizingRegions() {
        for (side in Side.entries) {
            val region = Region() styleClass "resize-region"
            when (side) {
                Side.TOP, Side.BOTTOM -> {
                    region.cursor = Cursors.RESIZE_VERTICAL
                    region.styleClass("resize-region-vertical")
                    region.layoutXProperty().bind(
                        this.prefWidthProperty().subtract(region.widthProperty()).divide(2.0)
                    )
                    region.prefWidthProperty().bind(this.prefWidthProperty().pow(0.75))
                }

                Side.LEFT, Side.RIGHT -> {
                    region.cursor = Cursors.RESIZE_HORIZONTAL
                    region.styleClass("resize-region-horizontal")
                    region.layoutYProperty().bind(
                        this.prefHeightProperty().subtract(region.heightProperty()).divide(2.0)
                    )
                    region.prefHeightProperty().bind(this.prefHeightProperty().pow(0.75))
                }
            }
            when (side) {
                Side.TOP -> {
                    region.translateYProperty().bind(region.heightProperty().divide(2.0).negate())
                    region.visibleProperty().bind(region.prefWidthProperty().greaterThan(8.0))
                }

                Side.BOTTOM -> {
                    region.layoutYProperty().bind(
                        this.heightProperty().subtract(region.heightProperty().divide(2))
                    )
                    region.visibleProperty().bind(region.prefWidthProperty().greaterThan(8.0))
                }

                Side.LEFT -> {
                    region.translateXProperty().bind(region.widthProperty().divide(2.0).negate())
                    region.visibleProperty().bind(region.prefHeightProperty().greaterThan(8.0))
                }

                Side.RIGHT -> {
                    region.layoutXProperty().bind(
                        this.widthProperty().subtract(region.widthProperty().divide(2))
                    )
                    region.visibleProperty().bind(region.prefHeightProperty().greaterThan(8.0))
                }
            }
            var dragStart: Point2D? = null
            var oldBounds: Bounds? = null
            region.addEventHandler(MouseEvent.ANY) { ev ->
                ev.consume()
                when (ev.eventType) {
                    MouseEvent.MOUSE_PRESSED -> {
                        oldBounds = BoundingBox(layoutX, layoutY, width, height)
                        dragStart = Point2D(ev.screenX, ev.screenY)
                        if (ev.isAltDown) {
                            if (side in setOf(Side.LEFT, Side.RIGHT)) {
                                isCreatingLoop = true
                            }
                        } else {
                            val resizeMode = when (ev.modifiers) {
                                noModifiers -> ScoreObject.ResizeMode.Regular
                                setOf(Shift) -> ScoreObject.ResizeMode.Stretch
                                setOf(Ctrl) -> ScoreObject.ResizeMode.DeepStretch
                                else -> return@addEventHandler
                            }
                            obj.beginResize(resizeMode, side)
                        }
                    }

                    MouseEvent.MOUSE_DRAGGED -> {
                        val start = dragStart ?: return@addEventHandler
                        if (obj.isResizing) {
                            val dx = ev.screenX - start.x
                            val dy = ev.screenY - start.y
                            resize(oldBounds!!, dx, dy, side)
                        }
                    }

                    MouseEvent.MOUSE_RELEASED -> {
                        if (isCreatingLoop) {
                            finishLoop()
                        } else if (obj.isResizing) {
                            obj.finishResize(recordEdit = true)
                        }
                    }
                }
            }
            children.add(region)
        }
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
                    selectView(addToSelection = false)
                    val toolPane = context[AppLayout].get<ScoreObjectDetailPane>()
                    toolPane.updateContent(this)
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

    fun cutObject(orientation: Orientation) {
        val p = screenToLocal(Robot().mousePosition)
        if (obj is ScoreObjectGroup && orientation == Orientation.VERTICAL) {
            val position = getScoreY(p.y)
            val (top, bottom) = (obj as ScoreObjectGroup).cutVertically(position)
            replaceWithCutHalves(top, bottom, relativePosition = ObjectPosition(zero, position))
        } else if (orientation == Orientation.HORIZONTAL) {
            val position = getDuration(p.x)
            if (position !in zero..obj.duration) return
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

    fun updateIsFocused(value: Boolean) {
        updateBorder()
    }

    fun updateIsSelected(value: Boolean) {
        updateBorder()
    }

    override fun updateIsSomeInstanceSelected(yesOrNo: Boolean) {
        updateBorder()
    }

    private fun updateBorder() {
        val selection = context[ScoreObjectSelectionManager]
        border = when {
            selection.focusedView.now == this -> solidBorder(Color.ORANGE, 2.0, BORDER_RADIUS)
            selection.isSelected(instance) -> solidBorder(borderColorWhenSelected, 2.0, BORDER_RADIUS)
            selection.isSelected(obj) -> solidBorder(borderColorWhenSameObjectSelected, 2.0, BORDER_RADIUS)
            else -> solidBorder(borderColorWhenNotSelected, 0.5, BORDER_RADIUS)
        }
    }

    /*
    * Dragging and resizing
    * */

    private fun startMove(ev: MouseEvent) {
        val selectionManager = context[ScoreObjectSelectionManager]
        if (!selectionManager.isSelected(this)) {
            selectView(addToSelection = ev.isShiftDown)
        }
        val selectedInstances = selectionManager.selectedInstances
        for (inst in selectedInstances) {
            inst.beginMove()
        }
    }

    private fun dragTo(toX: Double, toY: Double, ev: MouseEvent) {
        context[AppLayout].get<ScoreObjectDetailPane>().hidePopup()
        val movedObjects = context[ScoreObjectSelectionManager].selectedInstances + this.instance
        val minDeltaT = -movedObjects.minOf { inst -> inst.start }
        val maxDeltaT = movedObjects.minOf { inst -> inst.score!!.maxTime.now - inst.end }
        val minDeltaY = -movedObjects.minOf { inst -> inst.y }
        val maxDeltaY = movedObjects.minOf { inst -> inst.score!!.maxY.now - (inst.y + inst.height) }
        val (t, y) = parentPane.snapToGrid(toX, toY)
        var deltaT = (t - instance.start).coerceIn(minDeltaT..maxDeltaT)
        var deltaY = (y - instance.y).coerceIn(minDeltaY..maxDeltaY)
        if (ev.isShiftDown) deltaT = zero
        if (ev.isControlDown) deltaY = zero
        for (inst in movedObjects) {
            inst.moveTo(inst.start + deltaT, inst.y + deltaY, simpleMove = false)
        }
        parentPane.markT(instance.start + deltaT)
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
            var width = child.prefWidth(-1.0)
            if (child != box.children[0]) width += box.spacing
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

    private fun resize(old: Bounds, deltaX: Double, deltaY: Double, side: Side) {
        check(obj.canResize) { "Attempt to resize object that is not resizable" }
        context[AppLayout].get<ScoreObjectDetailPane>().hidePopup()
        val parentPane = parentPane
        if (side in setOf(Side.LEFT, Side.RIGHT)) {
            val oldX = if (side == Side.LEFT) old.minX else old.maxX
            val (t, _) = parentPane.snapToGrid(oldX + deltaX, old.maxY)
            parentPane.markT(t)
            val dt = t - parentPane.getTime(oldX)
            var newDur = getDuration(old.width)
            if (side == Side.LEFT) newDur -= dt
            else if (side == Side.RIGHT) newDur += dt
            newDur = newDur.coerceAtLeast(getDuration(10.0))
            if (side == Side.LEFT) newDur = newDur.coerceAtMost(instance.end)
            if (parentPane is SubScorePane && side == Side.RIGHT) newDur =
                newDur.coerceAtMost(parentPane.score.maxTime.now - instance.start)

            if (isCreatingLoop) {
                val factor = if (side == Side.LEFT) -1 else if (side == Side.RIGHT) +1 else return
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
                obj.resize(newDur, obj.height)
            }
        } else {
            var newHeight =
                if (side == Side.TOP) parentPane.getScoreY(old.height - deltaY)
                else parentPane.getScoreY(old.height + deltaY)
            newHeight = newHeight.coerceAtLeast(0.01.asY)
            if (side == Side.TOP) newHeight = newHeight.coerceAtMost(instance.y + instance.height)
            if (side == Side.BOTTOM) {
                val parentHeight = parentPane.associatedObject?.height ?: 1.0.asY
                newHeight = newHeight.coerceAtMost(parentHeight - instance.y)
            }
            obj.resize(obj.duration, newHeight)
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

    fun adjustHorizontal(direction: HorizontalDirection, resizeMode: ScoreObject.ResizeMode?) {
        check(obj.canResize) { "Cannot adjust horizontal $this because it has its own sub-window." }
        val parentPane = parentPane
        val deltaT = getDeltaT(direction)
        if (resizeMode != null) {
            val targetDuration = (obj.duration + deltaT).coerceAtMost(parentPane.score.maxTime.now - instance.start)
            obj.resize(
                targetDuration, obj.height,
                resizeMode, Side.RIGHT
            )
            parentPane.markT(instance.end)
        } else {
            val (t, _) = parentPane.snapToGrid(instance.position.plusTime(deltaT))
            val start = t.coerceIn(zero, parentPane.score.maxTime.now - obj.duration)
            instance.setTime(start)
            parentPane.markT(start)
        }
    }

    open fun adjustVertical(direction: VerticalDirection, resizeMode: ScoreObject.ResizeMode?) {
        var deltaY = 0.01.asTime
        if (direction == VerticalDirection.UP) deltaY *= -1
        adjustVertical(resizeMode, deltaY)
    }

    protected fun adjustVertical(resizeMode: ScoreObject.ResizeMode?, deltaY: Decimal) {
        val parentPane = parentPane
        if (resizeMode != null) {
            val targetHeight = (obj.height + deltaY).coerceAtMost(parentPane.score.maxY.now - instance.y)
            obj.resize(
                obj.duration, targetHeight,
                resizeMode, Side.BOTTOM
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
