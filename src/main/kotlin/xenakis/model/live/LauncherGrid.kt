package xenakis.model.live

import bundles.set
import fxutils.runFXWithTimeout
import fxutils.undo.PropertyEdit
import fxutils.undo.UndoManager
import hextant.context.Context
import hextant.context.extend
import hextant.core.editor.ListenerManager
import javafx.application.Platform
import javafx.geometry.HorizontalDirection
import javafx.scene.input.DataFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.*
import xenakis.impl.Logger
import xenakis.impl.div
import xenakis.impl.zero
import xenakis.model.Settings
import xenakis.model.flow.AudioFlows
import xenakis.model.obj.AbstractContextualObject
import xenakis.model.obj.ScoreObjectReference
import xenakis.model.player.ActiveScoreObject
import xenakis.model.player.ScoreObjectScheduler
import xenakis.model.player.ScorePlayer
import xenakis.model.project.mainScore
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.ObjectRegistry
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.registry.reference
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ScoreObject
import xenakis.model.score.ScoreObjectGroup
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject
import xenakis.ui.launcher.XenakisMainActivity
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
    private lateinit var scheduler: ScoreObjectScheduler

    @Transient
    private val activeObjects = mutableMapOf<GridItem, ActiveScoreObject?>()

    @Transient
    private val listeners = ListenerManager.createWeakListenerManager<View>()

    fun items(): List<GridItem> = items.asList()

    override fun initialize(context: Context) {
        undoManager = context[UndoManager].createSubManager()
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
        item.target.pressed()
        when (val target = item.target) {
            ItemTarget.None -> return
            is ItemTarget.Flow -> {
                val flow = target.ref.getFlow(context[AudioFlows]) ?: return
                if (!flow.isActive.now) flow.activate()
                else if (!item.stopOnRelease.now) {
                    flow.deactivate()
                }
            }

            is ItemTarget.Player -> Platform.runLater {
                val obj = target.ref.get() ?: return@runLater
                if (obj.player == null) {
                    context[XenakisMainActivity].scoreObjectsPane.listView.showContent(obj)
                }
                val player = obj.player
                if (player == null) {
                    Logger.warn("Player is null for ScoreObject ${obj.name.now}", Logger.Category.Playback)
                    return@runLater
                }
                if (!player.isPlaying.now) {
                    player.play()
                    context[XenakisMainActivity].scoreObjectsPane.listView.showContent(obj)
                } else if (!item.stopOnRelease.now) {
                    player.pause()
                }
            }

            is ItemTarget.Object -> {
                val obj = target.ref.get() ?: return
                val player = getPlayer()
                player.getClock().scheduleAction(obj.quantizationConfig) { quantizationDelay ->
                    val time = if (player.isPlaying.now) player.playHead.currentTime else zero
                    val y = obj.liveConfig.yPosition.now
                    val position = ObjectPosition(time, y)
                    val totalDelay = quantizationDelay.coerceAtMost(context[Settings].lookAhead)
                    ScorePlayer.execute {
                        activeObjects[item] = scheduler.scheduleObject(
                            obj, position, cutoff = zero, player,
                            scLangLatency = totalDelay / 2, serverLatency = totalDelay / 2
                        ) //TODO consider velocity
                    }
                }
            }
        }
    }

    private fun getScore() = when (val t = target.now) {
        is Target.SubScore -> t.ref.get()?.score
        Target.MainScore -> context[currentProject].mainScore
        Target.None -> null
    }

    private fun getPlayer() = when (val t = target.now) {
        is Target.SubScore -> t.ref.get()?.player ?: context[ScorePlayer.MAIN]
        Target.MainScore, Target.None -> context[ScorePlayer.MAIN]
    }

    fun noteOff(item: GridItem) {
        item.target.released()
        when (val target = item.target) {
            ItemTarget.None -> return
            is ItemTarget.Object -> {
                val activeObject = activeObjects[item] ?: return
                addToTargetScore(activeObject, item)
                if (!item.stopOnRelease.now) return
                ScorePlayer.execute {
                    scheduler.stopObjectInstantly(activeObject)
                }
                activeObjects[item] = null
            }

            is ItemTarget.Player -> {
                val player = target.ref.get()?.player ?: return
                if (player.isPlaying.now && item.stopOnRelease.now) {
                    player.pause()
                }
            }

            is ItemTarget.Flow -> {
                val flow = target.ref.getFlow(context[AudioFlows]) ?: return
                if (flow.isActive.now && item.stopOnRelease.now) {
                    flow.deactivate()
                }
            }
        }
    }

    private fun addToTargetScore(activeObject: ActiveScoreObject, item: GridItem) {
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
            score.addObject(obj, time, y * score.maxY.now, autoSelect = false)
        }
    }

    fun addView(view: View) {
        listeners.addListener(view)
    }

    @Serializable
    class GridItem(
        @SerialName("target") private var _target: ItemTarget = ItemTarget.None,
        val stopOnRelease: ReactiveVariable<Boolean> = reactiveVariable(false),
    ) : AbstractContextualObject() {
        @Transient
        private lateinit var grid: LauncherGrid

        var target: ItemTarget
            get() = _target
            set(value) {
                val oldTarget = _target
                if (value == oldTarget) return
                _target = value
                value.initialize(context)
                val description = if (value is ItemTarget.None) "Reset target" else "Choose target"
                grid.undoManager.record(PropertyEdit(this::target, oldTarget, value, description))
                grid.listeners.notifyListeners { updateItem(this@GridItem) }
            }

        fun reference() = GridItemReference(grid.items.indexOf(this))

        override fun initialize(context: Context) {
            super.initialize(context)
            target.initialize(context)
        }

        fun initialize(context: Context, grid: LauncherGrid) {
            initialize(context)
            this.grid = grid
        }
    }

    class GridItemReference(private val index: Int) : java.io.Serializable {
        fun getItem(grid: LauncherGrid) = grid.items[index]

        companion object {
            val DATA_FORMAT = DataFormat("xenakis/grid-item-reference")
        }
    }

    @Serializable
    sealed class ItemTarget : AbstractContextualObject() {
        open val targetObject: ScoreObject? get() = null

        abstract val canView: Boolean

        abstract val isActive: ReactiveBoolean

        open fun pressed() {}

        open fun released() {}

        @Serializable
        data object None : ItemTarget() {
            override val isActive: ReactiveBoolean
                get() = reactiveValue(false)

            override val canView: Boolean
                get() = false
        }

        @Serializable
        data class Object(val ref: ScoreObjectReference) : ItemTarget() {
            override val canView: Boolean
                get() = ref.isResolved.now

            override val targetObject: ScoreObject?
                get() = ref.get()

            @Transient
            private val active = reactiveVariable(false)

            override val isActive: ReactiveBoolean
                get() = active

            override fun initialize(context: Context) {
                ref.resolve(context[ScoreObjectRegistry])
            }

            override fun pressed() {
                active.set(true)
            }

            override fun released() {
                active.set(false)
            }

            override fun toString() = "Object ${ref.getName()}"
        }

        @Serializable
        data class Player(val ref: ScoreObjectReference) : ItemTarget() {
            override val canView: Boolean
                get() = ref.isResolved.now

            override val targetObject: ScoreObject?
                get() = ref.get()

            @Transient
            override lateinit var isActive: ReactiveBoolean

            override fun initialize(context: Context) {
                super.initialize(context)
                ref.resolve(context[ScoreObjectRegistry])
                if (context.hasProperty(XenakisMainActivity)) {
                    preparePlayer()
                }
            }

            fun preparePlayer() {
                val obj = targetObject
                if (obj == null) {
                    isActive = reactiveValue(false)
                    return
                }
                val scoreObjectsPane = context[XenakisMainActivity].scoreObjectsPane
                scoreObjectsPane.listView.initializeContent(obj)
                isActive = obj.player?.isPlaying ?: reactiveValue(false)
            }

            override fun toString() = "Player ${ref.getName()}"
        }

        @Serializable
        data class Flow(val ref: AudioFlows.FlowReference) : ItemTarget() {
            override val canView: Boolean
                get() = true

            @Transient
            override lateinit var isActive: ReactiveBoolean
                private set

            override fun initialize(context: Context) {
                super.initialize(context)
                isActive = ref.getFlow(context[AudioFlows])?.isActive ?: reactiveValue(false)
            }

            override fun toString(): String = "Flow ${ref.flowName}"
        }

        companion object {
            fun options(context: Context): List<ItemTarget> {
                val objects = context[ScoreObjectRegistry].filter { obj -> obj.affectsPlayback }
                val objectTargets = objects
                    .filter { obj -> obj !is ScoreObjectGroup }
                    .map { obj -> Object(obj.reference()) }
                val playerTargets = objects.map { obj -> Player(obj.reference()) }
                val flowTargets = context[AudioFlows].all().flatMap { group ->
                    group.flows.all().map { flow ->
                        val ref = AudioFlows.FlowReference(group.name.now, flow.name.now)
                        Flow(ref)
                    }
                }
                return listOf(None) + objectTargets + playerTargets + flowTargets
            }
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
        fun createNByN(n: Int) = LauncherGrid(Array(n * n) {
            GridItem(ItemTarget.None, stopOnRelease = reactiveVariable(false))
        })
    }
}