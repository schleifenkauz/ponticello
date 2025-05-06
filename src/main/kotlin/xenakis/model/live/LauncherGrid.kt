package xenakis.model.live

import fxutils.runFXWithTimeout
import hextant.context.Context
import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.impl.Logger
import xenakis.impl.zero
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
import xenakis.model.score.ScoreObjectGroup
import xenakis.model.score.ScoreObjectInstance
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject

@Serializable
class LauncherGrid private constructor(
    private val items: Array<Array<GridItem>>,
    val target: ReactiveVariable<Target> = reactiveVariable(Target.None),
) : AbstractContextualObject() {
    val itemIndices get() = 0 until items.size * items[0].size
    val rowIndices get() = items.indices
    val columnIndices get() = items[0].indices

    val isActive = reactiveVariable(false)

    @Transient
    private lateinit var scheduler: ScoreObjectScheduler

    @Transient
    private val activeObjects = arrayOfNulls<ActiveScoreObject>(items.size)

    @Transient
    val listeners = ListenerManager.createWeakListenerManager<Listener>()

    override fun initialize(context: Context) {
        super.initialize(context)
        val target = target.now
        if (target is Target.SubScore) {
            @Suppress("UNCHECKED_CAST")
            target.ref.resolve(context[ScoreObjectRegistry] as ObjectRegistry<ScoreObjectGroup>)
        }
        scheduler = context[ScoreObjectScheduler]
    }

    operator fun get(row: Int, column: Int) = items[row][column]

    fun getIndex(row: Int, column: Int) = row * items[0].size + column

    fun noteOn(index: Int, velocity: Int) {
        checkAttachedAndValidIndex(index) ?: return
        val (ref, y, freeOnRelease) = getItem(index)
        val active = activeObjects[index]
        if (freeOnRelease.now && active != null && active.stillActive) return
        val obj = ref.now.get()
        if (obj == null) {
            Logger.warn("Object at index $index is not resolved", Logger.Category.Playback)
            return
        }
        val player = getPlayer()
        val time = if (player.isPlaying.now) player.currentTime else zero
        val position = ObjectPosition(time, y.now)
        activeObjects[index] = scheduler.scheduleObject(
            obj, position, cutoff = zero, player,
            scLangLatency = zero, serverLatency = zero
        )
        listeners.notifyListeners { pressed(index) }
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

    private fun getItem(index: Int) = items[index / items.size][index % items.size]

    fun noteOff(index: Int) {
        checkAttachedAndValidIndex(index) ?: return
        listeners.notifyListeners { released(index) }
        val item = getItem(index)
        val activeObject = activeObjects[index]
        if (activeObject == null) {
            Logger.warn("Object at index $index was not active", Logger.Category.Playback)
            return
        }
        addToTargetScore(activeObject, item)
        if (!item.freeOnRelease.now) return
        scheduler.stopObjectInstantly(activeObject)
        activeObjects[index] = null
    }

    private fun addToTargetScore(activeObject: ActiveScoreObject, item: GridItem) {
        val score = getScore() ?: return
        val player = getPlayer()
        if (!player.isPlaying.now) return
        val pos = activeObject.absolutePosition
        var obj = activeObject.obj
        val cutoff = (pos.time + obj.duration) - player.currentTime
        if (item.freeOnRelease.now && cutoff > zero) {
            val name = context[ScoreObjectRegistry].availableName(obj.name.now)
            obj = obj.cut(obj.duration - cutoff, HorizontalDirection.LEFT, name) ?: obj
        }
        val inst = ScoreObjectInstance(obj, pos)
        runFXWithTimeout(50) { //timeout to avoid double playback (maybe solve this in a cleaner way...)
            score.addObject(inst, autoSelect = false)
        }
    }

    private fun checkAttachedAndValidIndex(index: Int): Int? {
        if (index !in itemIndices) {
            Logger.warn(
                "Invalid index $index for LauncherGrid of size ${items.size}x${items[0].size}",
                Logger.Category.Playback
            )
            return null
        }
        return index
    }

    @Serializable
    data class GridItem(
        val ref: ReactiveVariable<ScoreObjectReference>,
        val yPosition: ReactiveVariable<Decimal>,
        val freeOnRelease: ReactiveVariable<Boolean> = reactiveVariable(false),
    )

    @Serializable
    sealed class Target {
        @Serializable
        data class SubScore(val ref: ObjectReference<ScoreObjectGroup>) : Target() {
            override fun toString() = ref.getName()
        }

        @Serializable
        data object MainScore : Target() {
            override fun toString(): String = "Main Score"
        }

        @Serializable
        data object None : Target()

        companion object {
            fun options(context: Context): List<Target> {
                val subScores = context[ScoreObjectRegistry].filterIsInstance<ScoreObjectGroup>()
                val subScoreReferences = subScores.map { SubScore(it.reference()) }
                return listOf(None, MainScore) + subScoreReferences
            }
        }
    }

    interface Listener {
        fun pressed(index: Int)

        fun released(index: Int)
    }

    companion object {
        fun createNByN(n: Int) = LauncherGrid(Array(n) {
            Array(n) {
                GridItem(
                    reactiveVariable(ObjectReference.none()),
                    reactiveVariable(zero),
                    freeOnRelease = reactiveVariable(false)
                )
            }
        })
    }
}