package ponticello.model.live

import bundles.set
import fxutils.drag.TypedDataFormat
import fxutils.runFXWithTimeout
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import hextant.context.Context
import hextant.context.extend
import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.zero
import ponticello.model.obj.AbstractContextualObject
import ponticello.model.obj.project
import ponticello.model.player.ActiveScoreObject
import ponticello.model.player.ScoreObjectScheduler
import ponticello.model.player.ScorePlayer
import ponticello.model.project.mainScore
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.ObjectRegistry
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ScoreObjectGroup
import ponticello.model.score.SoundProcess
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import kotlin.math.sqrt

@Serializable
class LauncherGrid private constructor(
    private val items: Array<GridItem>,
    val target: ReactiveVariable<Target> = reactiveVariable(Target.None),
) : AbstractContextualObject() {
    @Transient
    lateinit var undoManager: UndoManager
        private set

    @Transient
    private val columns = sqrt(items.size.toDouble()).toInt()

    val isActive = reactiveVariable(false)

    @Transient
    lateinit var scheduler: ScoreObjectScheduler

    @Transient
    val activeObjects = mutableMapOf<GridItem, ActiveScoreObject?>()

    @Transient
    private val listeners = ListenerManager.createWeakListenerManager<View>()

    fun items(): List<GridItem> = items.asList()

    override fun initialize(context: Context) {
        undoManager = context[UndoManager]/*.createSubManager()*/
        val myContext = context.extend {
            set(UndoManager, undoManager)
        }
        super.initialize(myContext)
        scheduler = context[ScoreObjectScheduler]
        val target = target.now
        if (target is Target.SubScore) {
            @Suppress("UNCHECKED_CAST")
            target.ref.resolve(context[ScoreObjectRegistry] as ObjectRegistry<ScoreObjectGroup>)
        }
        for (item in items) {
            item.initialize(myContext, this)
        }
    }

    fun getGridIndices(item: GridItem): Pair<Int, Int> {
        val index = items.indexOf(item)
        val row = index / columns
        val column = index % columns
        return Pair(row, column)
    }

    operator fun get(row: Int, column: Int) = items[row * columns + column]

    fun swap(item1: GridItem, item2: GridItem) {
        val i = items.indexOf(item1)
        val j = items.indexOf(item2)
        items[j] = item1
        items[i] = item2
        listeners.notifyListeners {
            updateItem(item1)
            updateItem(item2)
        }
        undoManager.record(LauncherGridEdit.SwapItems(this, item1, item2))
    }

    fun noteOn(item: GridItem, velocity: Int) {
        item.target.pressed(velocity, item)
    }

    fun noteOff(item: GridItem) {
        item.target.released(item)
    }

    fun addToTargetScore(activeObject: ActiveScoreObject, item: GridItem) {
        val score = getScore() ?: return
        val player = getPlayer()
        if (!player.isPlaying.now) return
        val (time, y) = activeObject.absolutePosition
        var obj = activeObject.obj
        val cutoff = (time + obj.duration) - player.playHead.currentTime
        if (item.stopOnRelease.now && cutoff > zero) {
            val name = context[ScoreObjectRegistry].availableName(obj.name.now)
            obj = obj.cut(obj.duration - cutoff, HorizontalDirection.LEFT, name) ?: obj
        }
        runFXWithTimeout(50) { //timeout to avoid double playback (maybe solve this in a cleaner way...)
            score.addObject(obj, time, y * score.maxY, autoSelect = false)
        }
    }

    private fun getScore() = when (val t = target.now) {
        is Target.SubScore -> t.ref.get()?.score
        Target.MainScore -> context.project.mainScore
        Target.None -> null
    }

    fun getPlayer() = when (val t = target.now) {
        is Target.SubScore -> t.ref.get()?.player ?: context[ScorePlayer.MAIN]
        Target.MainScore, Target.None -> context[ScorePlayer.MAIN]
    }

    fun addView(view: View) {
        listeners.addListener(view)
    }

    @Serializable
    class GridItem(
        @SerialName("target") private val _target: ReactiveVariable<ItemTarget> = reactiveVariable(ItemTarget.None()),
        val stopOnRelease: ReactiveVariable<Boolean> = reactiveVariable(false),
    ) : AbstractContextualObject() {
        @Transient
        private lateinit var grid: LauncherGrid

        var target: ItemTarget
            get() = _target.now
            set(value) {
                val oldTarget = _target.now
                if (value == oldTarget) return
                _target.now = value
                value.initialize(grid)
                val description = if (value is ItemTarget.None) "Reset target" else "Choose target"
                grid.undoManager.record(VariableEdit(_target, oldTarget, value, description))
                grid.listeners.notifyListeners { updateItem(this@GridItem) }
                if (value is ItemTarget.Object) {
                    val target = value.targetObject
                    if (target is SoundProcess && target.def.hasParameter("level")) {
                        value.velocityParameter.now = target.def.getParameter("level")!!.reference()
                    }
                }
            }

        fun target(): ReactiveValue<ItemTarget> = _target

        fun reference() = GridItemReference(grid.items.indexOf(this))

        override fun initialize(context: Context) {
            super.initialize(context)
            target.initialize(grid)
        }

        fun initialize(context: Context, grid: LauncherGrid) {
            this.grid = grid
            initialize(context)
        }

        companion object {
            fun createDefault(): GridItem = GridItem(
                _target = reactiveVariable(ItemTarget.None()),
                stopOnRelease = reactiveVariable(false),
            )
        }
    }

    class GridItemReference(private val index: Int) : java.io.Serializable {
        fun getItem(grid: LauncherGrid) = grid.items[index]

        companion object {
            val DATA_FORMAT = TypedDataFormat<GridItemReference>("ponticello/grid-item-reference")
        }
    }

    @Serializable
    sealed interface Target {
        @Serializable
        data class SubScore(val ref: ObjectReference<ScoreObjectGroup>) : Target {
            override fun toString() = ref.getName()
        }

        @Serializable
        data object MainScore : Target {
            override fun toString(): String = "Main Score"
        }

        @Serializable
        data object None : Target

        companion object {
            fun options(context: Context): List<Target> {
                val subScores = context[ScoreObjectRegistry].filterIsInstance<ScoreObjectGroup>()
                val subScoreReferences = subScores.map { SubScore(it.reference()) }
                return listOf(None, MainScore) + subScoreReferences
            }
        }
    }

    interface View {
        fun updateItem(item: GridItem)
    }

    companion object {
        fun createNByN(n: Int) = LauncherGrid(Array(n * n) { GridItem.createDefault() })
    }
}