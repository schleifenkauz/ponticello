package xenakis.ui.score

import fxutils.*
import fxutils.actions.ActionBar
import fxutils.actions.registerShortcuts
import fxutils.prompt.CompoundPrompt
import fxutils.prompt.DetailPane
import fxutils.prompt.compoundPrompt
import hextant.context.Context
import hextant.undo.UndoManager
import hextant.undo.compoundEdit
import javafx.geometry.Bounds
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.VerticalDirection
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.control.ColorPicker
import javafx.scene.control.Spinner
import javafx.scene.control.TextField
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.scene.paint.Color
import javafx.scene.paint.Color.BLACK
import javafx.stage.Screen
import javafx.stage.Window
import reaktive.value.ReactiveValue
import reaktive.value.binding.map
import reaktive.value.binding.orElse
import reaktive.value.forEach
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.player.PlaybackManager
import xenakis.model.project.UIState.SnapOption
import xenakis.model.project.settings
import xenakis.model.score.*
import xenakis.model.score.Score.Companion.rootScore
import xenakis.ui.actions.ObjectActionContext
import xenakis.ui.actions.ObjectActions
import xenakis.ui.controls.NameControl
import xenakis.ui.impl.Direction
import xenakis.ui.impl.isResizeCursor
import xenakis.ui.impl.makeSubWindow
import xenakis.ui.impl.resizeDirection
import xenakis.ui.impl.resizeMode
import xenakis.ui.impl.setupDraggingAndResizing
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject

abstract class ScoreObjectView(
    val instance: ScoreObjectInstance,
) : Pane(), ScoreObjectInstance.Listener, ScoreObject.Listener, TimeBlock {
    var isInitialized: Boolean = false
        private set
    lateinit var pane: ScorePane
        private set

    open val obj: ScoreObject get() = instance.obj

    protected var isSubWindow: Boolean = false
        private set

    lateinit var context: Context
        private set

    private var isCreatingLoop = false
    private val loopedObjects = mutableListOf<ScoreObjectInstance>()

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

    override fun getDuration(width: Double): Decimal =
        if (!isSubWindow) pane.getDuration(width) else (width * (instance.obj.duration / this.width))

    override fun getWidth(duration: Decimal): Double =
        if (!isSubWindow) pane.getWidth(duration) else (duration * (width / instance.obj.duration)).toDouble()

    override fun getTime(x: Double): Decimal = getDuration(x)

    override fun getX(time: Decimal): Double = getWidth(time)

    fun getScoreY(screenY: Double): Decimal =
        if (!isSubWindow) pane.getScoreY(screenY) else (screenY * (instance.obj.height / this.height))

    fun getScreenY(scoreY: Decimal): Double =
        if (!isSubWindow) pane.getScreenY(scoreY) else (scoreY * (height / instance.obj.height)).toDouble()

    fun getDetailPane(): DetailPane {
        val detailPane = DetailPane(labelWidth = 100.0)
        val obj = instance.obj
        if (obj is ScoreObject.Unresolved) {
            return detailPane
        } else {
            val ctx = ObjectActionContext.SingleObjectContext(this)
            detailPane.children.add(
                HBox(
                    5.0,
                    NameControl(obj),
                    ActionBar(ObjectActions.multiObjectActions.withContext(ctx), buttonStyle = "medium-icon-button"),
                    ActionBar(ObjectActions.singleObjectActions.withContext(ctx), buttonStyle = "medium-icon-button"),
                    ActionBar(ObjectActions.playbackActions.withContext(ctx), buttonStyle = "medium-icon-button"),
                    *headerItems().toTypedArray()
                ).centerChildren().pad(8.0)
            )
            detailPane.registerShortcuts(ObjectActions.all.withContext(ctx))
            if (obj.canResize) {
                val durationLabel = label(obj.duration().map { dur -> "${dur.toCanonicalString()} seconds" }).pad(5.0)
                detailPane.addItem("Duration", durationLabel)
            }
            setupDetailPane(detailPane)
            return detailPane
        }
    }

    protected open fun headerItems(): List<Node> = emptyList()

    protected open fun setupDetailPane(pane: DetailPane) {}

    fun askForLoopConfig(): LoopConfig? {
        if (isSubWindow) return null
        val settings = context[currentProject].settings
        val grid = pane.getNearestGrid(instance.position)?.obj
            .takeIf { settings.snapEnabled.now } as TempoGridObject?
        val periodUnit = if (grid == null) null else settings.snapOption.now
        val prompt = makeLoopConfigPrompt(periodUnit, grid, instance)
        return prompt.showDialog(anchorNode = this)
    }

    protected open fun initialize() {
        initializeLayout()
        setBackground()

        instance.addListener(this)
        instance.obj.addListener(this)
        isInitialized = true
    }

    open fun initialize(parent: ScorePane) {
        if (isInitialized) return
        pane = parent
        context = parent.context
        initialize()
        val canResize = obj.canResize
        setupDraggingAndResizing(
            parent.context,
            canUserChangeWidth = canResize, canUserChangeHeight = canResize,
            drag = this::dragTo, resize = this::resize,
            startDrag = this::startDrag, finishDrag = this::finishedDrag
        )
        addMouseActions()
    }

    open fun initialize(parent: ScorePane, window: Window) {
        if (isInitialized) return
        pane = parent
        context = pane.context
        isSubWindow = true
        heightProperty().addListener { _, _, _ -> rescale() }
        widthProperty().addListener { _, _, _ -> rescale() }
        initialize()
    }

    protected fun selectThis(addToSelection: Boolean) {
        toFront()
        context[ScoreObjectSelectionManager].select(this, addToSelection = addToSelection)
        requestFocus()
    }

    private fun initializeLayout() {
        if (!isSubWindow) {
            relocate(pane.getX(instance.start), pane.getScreenY(instance.y))
        }
        setPrefSize(getDisplayWidth(), getDisplayHeight())
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
        opacity = if (muted) 0.5 else 1.0
    }

    private fun addMouseActions() {
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            when {
                ev.button == MouseButton.PRIMARY && ev.clickCount == 2 -> showInSubWindow()

                ev.button == MouseButton.PRIMARY && ev.modifiers.isEmpty() -> {
                    val playback = context[PlaybackManager]
                    if (!playback.isPlaying.now && playback.isAttachedTo(this)) {
                        playback.playHead.setPlayHeadX(ev.x)
                    }
                }

                else -> return@addEventHandler
            }
            ev.consume()
        }
    }

    private fun cutObject(ev: MouseEvent) {
        val obj = instance.obj
        if (obj is ScoreObjectGroup && ev.isShiftDown) {
            val position = getScoreY(ev.y)
            val (top, bottom) = obj.cutVertically(position)
            replaceWithCutHalves(top, bottom, relativePosition = ObjectPosition(zero, position))
        } else if (!ev.isShiftDown) {
            val position = getDuration(ev.x)
            val leftHalf = obj.cut(position, LEFT, "${obj.name.now}_left") ?: return
            val rightHalf = obj.cut(position, RIGHT, "${obj.name.now}_right") ?: return
            replaceWithCutHalves(leftHalf, rightHalf, relativePosition = ObjectPosition(position, zero))
        }
    }

    private fun showInSubWindow() {
        if (isSubWindow) return
        val view = pane.createObjectView(instance)
        val maxDimensions = Screen.getPrimary().visualBounds
        val window = makeSubWindow(view, obj.name.map { n -> "Object $n" }, context)
        window.width = (this.prefWidth * 3).coerceAtMost(maxDimensions.width)
        window.height = (this.prefHeight * 3).coerceAtMost(maxDimensions.height)
        window.show()
        view.initialize(pane, window)
    }

    private fun replaceWithCutHalves(half1: ScoreObject, half2: ScoreObject, relativePosition: ObjectPosition) {
        context.compoundEdit("Cut object") {
            for (inst in context[rootScore].instancesOf(instance.obj).toList()) {
                val score = inst.score
                score!!.removeObject(inst, Score.RegistryOption.KEEP_IN_REGISTRY)
                val inst1 = ScoreObjectInstance(half1, inst.position, inst.muted.copy())
                val inst2 = ScoreObjectInstance(half2, inst.position + relativePosition, inst.muted.copy())
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
        val selectionManager = context[ScoreObjectSelectionManager]
        if (cursor.isResizeCursor) {
            selectionManager.deselectAll()
            if (ev.isAltDown) {
                isCreatingLoop = true
                return true
            } else {
                val direction = cursor.resizeDirection()
                return instance.obj.beginResize(ev.resizeMode ?: return false, direction)
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
        if (isCreatingLoop) {
            isCreatingLoop = false
            return
        }
        if (cursor.isResizeCursor) {
            instance.obj.finishResize()
        } else {
            val selectedInstances = context[ScoreObjectSelectionManager].selectedInstances + this.instance
            for (inst in selectedInstances) {
                inst.finishMove()
            }
        }
    }

    private fun dragTo(toX: Double, toY: Double) {
        val movedObjects = context[ScoreObjectSelectionManager].selectedInstances + this.instance
        val minDeltaT = -movedObjects.minOf { inst -> inst.start }
        val maxDeltaT = movedObjects.minOf { inst -> inst.score!!.maxTime - inst.end }
        val minDeltaY = -movedObjects.minOf { inst -> inst.y }
        val maxDeltaY = movedObjects.minOf { inst -> inst.score!!.maxY - (inst.y + inst.height) }
        val (t, y) = pane.snapToGrid(toX, toY)
        val deltaT = (t - instance.start).coerceIn(minDeltaT..maxDeltaT)
        val deltaY = (y - instance.y).coerceIn(minDeltaY..maxDeltaY)
        for (inst in movedObjects) {
            inst.moveTo(inst.start + deltaT, inst.y + deltaY, simpleMove = false)
        }
        pane.markT(instance.start + deltaT)
    }

    open fun getDisplayWidth(): Double = getWidth(instance.duration)

    open fun getDisplayHeight() = getScreenY(instance.height)

    open fun rescale() {}

    final override fun moved(start: Decimal, y: Decimal) {
        if (!isSubWindow) relocate(getX(instance.start), getScreenY(y))
    }

    override fun resizedObject(obj: ScoreObject) {
        if (!isSubWindow) {
            setPrefSize(getDisplayWidth(), getDisplayHeight())
        }
        rescale()
    }

    @Suppress("UNUSED_PARAMETER") //parameter [ev] is needed to be compatible with Node.setupDraggingAndResizing
    private fun resize(old: Bounds, deltaX: Double, deltaY: Double, cursor: Cursor, ev: MouseEvent) {
        check(!isSubWindow) { "Cannot resize $this because it has its own sub window." }
        check(instance.obj.canResize) { "Attempt to resize object that is not resizable" }
        val direction = cursor.resizeDirection()
        val oldX = if (direction.left) old.minX else old.maxX
        val oldY = if (direction.up) old.minY else old.maxY
        val (t, y) = pane.snapToGrid(oldX + deltaX, oldY + deltaY)
        pane.markT(t)
        val dt = t - pane.getTime(oldX)
        val dy = y - pane.getScoreY(oldY)
        var newDur = getDuration(old.width)
        if (direction.left) newDur -= dt
        else if (direction.right) newDur += dt
        var newHeight = pane.getScoreY(old.height)
        if (direction.up) newHeight -= dy
        else if (direction.down) newHeight += dy

        newDur = newDur.coerceAtLeast(getDuration(10.0))
        newHeight = newHeight.coerceAtLeast(0.01.asY)

        if (direction.left) newDur = newDur.coerceAtMost(instance.end)
        if (pane is SubScorePane && direction.right) newDur =
            newDur.coerceAtMost(pane.associatedObject!!.duration - instance.start)
        if (direction.up) newHeight = newHeight.coerceAtMost(instance.y + instance.height)
        if (direction.down) {
            val parentHeight = pane.associatedObject?.height ?: 1.0.asY
            newHeight = newHeight.coerceAtMost(parentHeight - instance.y)
        }
        if (isCreatingLoop) {
            val loopCount = (newDur / obj.duration).roundToInt() - 1
            if (loopCount > loopedObjects.size) {
                for (i in loopedObjects.size until loopCount) {
                    val offset = ObjectPosition(i * obj.duration, zero)
                    val obj = ScoreObjectInstance(obj, instance.position + offset, instance.muted.copy())
                    loopedObjects.add(obj)
                    pane.score.addObject(obj)
                }
            } else {
                for (i in loopedObjects.size - 1 downTo loopCount) {
                    val inst = loopedObjects.removeAt(i)
                    pane.score.removeObject(inst, Score.RegistryOption.KEEP_IN_REGISTRY)
                }
            }
        } else {
            instance.obj.resize(newDur, newHeight)
        }
    }

    fun getDeltaT(direction: HorizontalDirection): Decimal {
        val factor = if (direction == LEFT) -3.0 else 3.0
        val grid = pane.getNearestGrid(instance.position)?.obj as TempoGridObject?
        val settings = context[currentProject].settings
        val snapOption = if (settings.snapEnabled.now) settings.snapOption.now else null
        val deltaT =
            if (snapOption != null && grid != null) grid.getDuration(snapOption) * factor
            else getDuration(factor)
        return deltaT
    }

    fun adjustHorizontal(direction: HorizontalDirection, resize: Boolean, resizeMode: ScoreObject.ResizeMode?) {
        check(instance.obj.canResize) { "Cannot adjust horizontal $this because it has its own sub-window." }
        val deltaT = getDeltaT(direction)
        if (resize) {
            val targetDuration = (instance.obj.duration + deltaT).coerceAtMost(pane.score.maxTime - instance.start)
            instance.obj.resize(
                targetDuration, instance.obj.height,
                resizeMode!!, Direction.horizontal(RIGHT)
            )
            pane.markT(instance.end)
        } else {
            val (t, _) = pane.snapToGrid(instance.position.plusTime(deltaT))
            val start = t.coerceIn(zero, pane.score.maxTime - instance.obj.duration)
            instance.setTime(start)
            pane.markT(start)
        }
    }

    open fun adjustVertical(direction: VerticalDirection, resize: Boolean, resizeMode: ScoreObject.ResizeMode) {
        var deltaY = 0.01.asTime
        if (direction == VerticalDirection.UP) deltaY *= -1
        adjustVertical(resize, resizeMode, deltaY)
    }

    protected fun adjustVertical(resize: Boolean, resizeMode: ScoreObject.ResizeMode, deltaY: Decimal) {
        check(!isSubWindow) { "Cannot adjust vertical $this because it has its own sub-window." }
        if (resize) {
            val targetHeight = (instance.obj.height + deltaY).coerceAtMost(pane.score.maxY - instance.y)
            instance.obj.resize(
                instance.obj.duration, targetHeight,
                resizeMode, Direction.vertical(VerticalDirection.DOWN)
            )
        } else {
            val y = (instance.y + deltaY).coerceIn(zero, pane.score.maxY - instance.obj.height)
            instance.setY(y)
        }
    }

    override fun toString(): String = "ScoreObjectView for $instance"

    companion object {
        private const val BORDER_WIDTH = 3.0
        private const val BORDER_RADIUS = 2.0

        private fun makeLoopConfigPrompt(
            periodUnit: SnapOption?,
            grid: TempoGridObject?,
            inst: ScoreObjectInstance,
        ): CompoundPrompt<LoopConfig> {
            val prompt = compoundPrompt("Configure loop of object ${inst.obj.name.now}") {
                val periodInput = when (periodUnit) {
                    null -> TextField(inst.duration.toString())
                    else -> {
                        val default = (inst.duration / grid!!.getDuration(periodUnit) + 0.95).toInt()
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
                    LoopConfig(period, repetitions)
                }
            }
            return prompt
        }
    }
}
