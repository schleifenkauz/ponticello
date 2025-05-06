package xenakis.model.live

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveBoolean
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.Decimal
import xenakis.impl.Logger
import xenakis.impl.zero
import xenakis.model.obj.AbstractContextualObject
import xenakis.model.obj.ScoreObjectReference
import xenakis.model.player.ActiveScoreObject
import xenakis.model.player.ScorePlayer
import xenakis.model.registry.ObjectReference
import xenakis.model.score.ObjectPosition
import xenakis.ui.midi.ContextualMidiReceiver

@Serializable
class LauncherGrid private constructor(private val items: Array<Array<GridItem>>) : AbstractContextualObject() {
    val itemIndices get() = 0 until items.size * items[0].size
    val rowIndices get() = items.indices
    val columnIndices get() = items[0].indices

    val isActive = reactiveVariable(false)
    val addToScore = reactiveVariable(false)

    @Transient
    private val activeObjects = arrayOfNulls<ActiveScoreObject>(items.size)

    @Transient
    private var player: ScorePlayer? = null

    @Transient
    private val attached = reactiveVariable(false)

    val isAttached: ReactiveBoolean get() = attached

    fun attachTo(player: ScorePlayer) {
        this.player = player
        attached.now = true
        context[ContextualMidiReceiver].attachGrid(this)
    }

    operator fun get(row: Int, column: Int) = items[row][column]

    fun noteOn(index: Int, velocity: Int) {
        val player = checkAttachedAndValidIndex(index) ?: return
        val (ref, y, freeOnRelease) = getItem(index)
        val active = activeObjects[index]
        if (freeOnRelease.now && active != null && active.stillActive) return
        val obj = ref.now.get()
        if (obj == null) {
            Logger.warn("Object at index $index is not resolved", Logger.Category.Playback)
            return
        }
        val time = if (player.isPlaying.now) player.currentTime else zero
        val position = ObjectPosition(time, y.now)
        activeObjects[index] = player.scheduler.scheduleObject(obj, position, cutoff = zero)
    }

    private fun getItem(index: Int) = items[index / items.size][index % items.size]

    fun noteOff(index: Int) {
        val player = checkAttachedAndValidIndex(index) ?: return
        val item = getItem(index)
        if (!item.freeOnRelease.now) return
        val activeObject = activeObjects[index]
        if (activeObject == null) {
            Logger.warn("Object at index $index was not active", Logger.Category.Playback)
            return
        }
        player.scheduler.stopObjectInstantly(activeObject)
        activeObjects[index] = null
    }

    private fun checkAttachedAndValidIndex(index: Int): ScorePlayer? {
        val player = player
        if (player == null) {
            Logger.warn("LauncherGrid is not attached to any player", Logger.Category.Playback)
            return null
        }
        if (index !in itemIndices) {
            Logger.warn(
                "Invalid index $index for LauncherGrid of size ${items.size}x${items[0].size}",
                Logger.Category.Playback
            )
            return null
        }
        return player
    }

    @Serializable
    data class GridItem(
        val ref: ReactiveVariable<ScoreObjectReference>,
        val yPosition: ReactiveVariable<Decimal>,
        val freeOnRelease: ReactiveVariable<Boolean> = reactiveVariable(false)
    )

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