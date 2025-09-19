package ponticello.ui.score

import fxutils.*
import fxutils.drag.setupDropArea
import hextant.context.Context
import hextant.context.compoundEdit
import javafx.geometry.Bounds
import javafx.geometry.Point2D
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.Pane
import ponticello.impl.*
import ponticello.model.obj.SampleObject
import ponticello.model.obj.project
import ponticello.model.project.UI_STATE
import ponticello.model.project.get
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.*
import ponticello.ui.misc.TempoSyncPrompt
import reaktive.value.now
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

abstract class ScorePane(val score: Score, val context: Context) : Pane(), ScoreListener, TimeBlock {
    protected var lastMousePress: Point2D? = null
        private set

    protected val views = mutableMapOf<ScoreObjectInstance, ScoreObjectView>()
    val allViews: Collection<ScoreObjectView> get() = views.values

    protected val selector: ScoreObjectSelectionManager get() = context[ScoreObjectSelectionManager]

    abstract val root: ScorePane

    protected abstract val displayStart: Decimal
    protected abstract val displayEnd: Decimal

    override val timeRange: DecimalRange
        get() = displayStart..displayEnd

    abstract val yRange: DecimalRange

    abstract val associatedObject: ScoreObject?

    abstract val pixelsPerSecond: Double

    init {
        styleClass("score-pane")
    }

    open fun isRoot(obj: ScoreObject) = false

    open fun snapToGrid(position: ObjectPosition): ObjectPosition =
        root.snapToGrid(position + absolutePosition) - absolutePosition

    open fun getNearestGrid(position: ObjectPosition): TempoGrid? =
        root.getNearestGrid(position + absolutePosition)

    open fun snapToGrid(x: Double, y: Double): ObjectPosition = snapToGrid(ObjectPosition(getTime(x), getScoreY(y)))

    open fun markT(t: Decimal) {
        val time = t + absolutePosition.time
        root.markT(time)
    }

    override fun getX(time: Decimal): Double = ((time - displayStart) * pixelsPerSecond).toDouble()

    override fun getTime(x: Double): Decimal = (x / pixelsPerSecond) + displayStart

    override fun getDuration(width: Double): Decimal = (width / pixelsPerSecond).asTime

    override fun getWidth(duration: Decimal): Double = (duration * pixelsPerSecond).toDouble()

    abstract fun getScoreY(screenY: Double): Decimal

    abstract fun getScreenY(scoreY: Decimal): Double

    open fun coerceAndTransformScoreY(y: Decimal, obj: ScoreObject) = y.coerceIn(score.minY, score.maxY - obj.height)

    protected open fun deleteTimeRange(start: Decimal, end: Decimal) {
        score.deleteTimeRange(start, end)
    }

    open fun repaint(): Future<*> {
        removeOutOfRangeChildren()
        val future = CompletableFuture<Unit>()
        layoutObjects(views.iterator(), Long.MAX_VALUE, future)
        return future
    }

    protected fun removeOutOfRangeChildren() {
        children.removeIf { child ->
            if (child !is ScoreObjectView) false
            else {
                val inst = child.instance
                inst.start > displayEnd || inst.start + inst.duration < displayStart
            }
        }
    }

    protected fun layoutObjects(
        itr: Iterator<Map.Entry<ScoreObjectInstance, ScoreObjectView>>,
        maxTime: Long, job: CompletableFuture<Unit>,
    ) {
        val tStart = System.currentTimeMillis()
        while (itr.hasNext()) {
            val (inst, view) = itr.next()
            if (inst.start > displayEnd || inst.start + inst.duration < displayStart) {
                continue
            }
            val resizeHorizontal = !view.prefWidth.isFinite() || view.getDisplayWidth() != view.prefWidth
            val resizeVertical = !view.prefHeight.isFinite() || view.getDisplayHeight() != view.prefHeight
            if (resizeHorizontal || resizeVertical) {
                view.setPrefSize(view.getDisplayWidth(), view.getDisplayHeight())
                view.rescale()
            }
            view.relocate(getX(inst.start), getScreenY(inst.y))
            if (view !in children) children.add(view)
            if (System.currentTimeMillis() - tStart > maxTime) {
                break
            }
        }
        job.complete(Unit)
    }

    protected open fun listenForEvents() {
        setupDropArea(ScorePaneDropHandler(this))
        addEventHandler(MouseEvent.ANY) { ev ->
            if (ev.target != this) return@addEventHandler
            when (ev.eventType) {
                MouseEvent.MOUSE_MOVED -> mouseMoved(ev)
                MouseEvent.MOUSE_EXITED -> mouseExited()
                MouseEvent.MOUSE_PRESSED -> lastMousePress = Point2D(ev.x, ev.y)
                MouseEvent.DRAG_DETECTED -> mouseDragDetected(ev)
                MouseEvent.MOUSE_DRAGGED -> mouseDragged(ev)
                MouseEvent.MOUSE_CLICKED -> {
                    val p = Point2D(ev.x, ev.y)
                    if (lastMousePress != null && p.distance(lastMousePress) > DRAG_THRESH) return@addEventHandler
                    when {
                        ev.button == MouseButton.SECONDARY -> rightClicked(ev)
                        ev.clickCount == 1 -> mouseClicked(ev)
                        ev.clickCount == 2 -> doubleClicked(ev)
                    }
                }

                MouseEvent.MOUSE_RELEASED -> mouseReleased(ev)
            }
        }
    }

    /*
    * Score object view management
    * +*/

    fun getObjectView(inst: ScoreObjectInstance) = views[inst] ?: error("No view found for ${inst.ref.getName()}")

    protected abstract fun acceptObject(obj: ScoreObject): ScoreObject?

    /*
    * Score change handlers
    * */

    override fun addedObject(score: Score, inst: ScoreObjectInstance, autoSelect: Boolean) {
        val view = createObjectView(inst.obj, inst)
        view.initialize(this)
        view.relocate(getX(inst.start), getScreenY(inst.y))
        view.setPrefSize(view.getDisplayWidth(), view.getDisplayHeight())
        views[inst] = view
        children.add(view)
        runAfterLayout {
            view.rescale()
            if (autoSelect) {
                view.selectView(addToSelection = false)
            }
        }
    }

    override fun removedObject(score: Score, inst: ScoreObjectInstance) {
        val view = views.remove(inst) ?: return
        context[ScoreObjectSelectionManager].removed(view)
        children.remove(view)
    }

    /*
    * Mouse events
    * */

    protected open fun mouseMoved(ev: MouseEvent) {

    }

    protected open fun mouseExited() {

    }

    protected open fun mouseClicked(ev: MouseEvent) {
        val (t, y) = snapToGrid(ev.x, ev.y)
        val duplicator = context[ScoreObjectDuplicator]
        when {
            duplicator.isInDuplicateMode() -> {
                var obj = duplicator.clipboardObject!!
                obj = acceptObject(obj) ?: return
                if (obj.height > score.maxY || obj.duration > score.maxTime) return
                if (duplicator.isCloneMode) {
                    val name = context[ScoreObjectRegistry].nameForClone(obj, ev) ?: return
                    obj = obj.clone(name)
                }
                val time = t.coerceIn(zero, score.maxTime - obj.duration)
                val scoreY = coerceAndTransformScoreY(y, obj)
                if (context.project[UI_STATE].snapEnabled.now && obj is SoundProcess) {
                    val sample = obj.sample.now?.get() as? SampleObject
                    val grid = getNearestGrid(ObjectPosition(t, y))
                    val rateControl = obj.playBufRate
                    if (grid != null && rateControl != null && sample != null && !sample.meter.isNone()) {
                        val trackBpm = sample.meter.beatsPerMinute.now
                        if (trackBpm != grid.bpm) {
                            val rate = TempoSyncPrompt.create(grid.bpm, trackBpm)
                                .showDialog(ev, preferMouseCoords = true)
                            if (rate != null) {
                                rateControl.set(rate)
                            }
                        }
                    }
                }
                score.addObject(obj, time, scoreY, autoSelect = false)
                ev.consume()
            }

            this is RootScorePane && ev.modifiers.isEmpty() -> {
                ev.consume()
                selector.deselectAll()
                requestFocus()
                if (playHead.canMoveManually.now) { //TODO pause and replay if currently playing
                    playHead.movePlayHead(t)
                }
            }

            else -> {}
        }
    }

    protected open fun rightClicked(ev: MouseEvent) {
        when (ev.modifiers) {
            setOf(Alt, Shift) -> {
                pasteFromSystemClipboard(ev)
            }
        }
    }

    protected open fun doubleClicked(ev: MouseEvent) {

    }

    protected open fun mouseDragDetected(ev: MouseEvent) {
        val pos = snapToGrid(ev.x, ev.y)
        RectangleSelection.create(this, pos)
    }

    protected open fun mouseDragged(ev: MouseEvent) {
        ev.consume()
        val selectedArea = RectangleSelection.get(this)
        if (selectedArea != null) {
            var pos = snapToGrid(ev.x, ev.y)
            pos = ObjectPosition(pos.time.coerceIn(timeRange), pos.y.coerceIn(yRange))
            selectedArea.setOppositeCorner(pos)
            markT(pos.time)
        }
    }

    protected open fun mouseReleased(ev: MouseEvent) {
        val selection = RectangleSelection.get(this)
        if (selection == null || selection.isEmpty()) return
        if (ev.isControlDown || ev.isShiftDown || ev.isAltDown) {
            RectangleSelection.clear()
            val containedViews = viewsInside(selection.bounds, mustBeContainedEntirely = ev.isAltDown)
            selector.selectAll(containedViews, addToSelection = ev.isShiftDown)
            requestFocus()
        }
    }

    private fun pasteFromSystemClipboard(ev: MouseEvent) {
        var instances = context[ScoreObjectSelectionManager].getSystemClipboard() ?: return
        context.compoundEdit("Paste objects") {
            if (ev.isShiftDown) {
                instances = instances.map { inst -> inst.clone() }
            }
            val leftTop = instances.minOf { it.position }
            selector.deselectAll()
            val (t, y) = snapToGrid(ev.x, ev.y)
            for (inst in instances) {
                score.addObject(inst, autoSelect = true)
                inst.moveTo(t - leftTop.time, y - leftTop.y, simpleMove = true)
            }
            val views = instances.map { inst -> getObjectView(inst) }
            selector.selectAll(views, addToSelection = false)
        }
    }

    fun viewsInside(bounds: Bounds, mustBeContainedEntirely: Boolean) = children
        .filterIsInstance<ScoreObjectView>()
        .filter { v ->
            v in children &&
                    if (mustBeContainedEntirely) bounds.contains(v.boundsInParent)
                    else bounds.intersects(v.boundsInParent)
        }

    companion object {
        fun createObjectView(obj: ScoreObject, instance: ScoreObjectInstance): ScoreObjectView = when (obj) {
            is SoundProcess -> SoundProcessView(obj, instance)
            is TaskObject -> TaskObjectView(obj, instance)
            is MemoObject -> MemoObjectView(obj, instance)
            is ScoreObjectGroup -> ScoreObjectGroupView(obj, instance)
            is TempoGridObject -> TempoGridObjectView(obj, instance)
            is MidiObject -> MidiObjectView(obj, instance)
            is MidiNoteObject -> MidiNoteObjectView(obj, instance)
            is UnresolvedScoreObject -> UnresolvedScoreObjectView(instance)
        }

        private const val DRAG_THRESH = 5.0
    }
}