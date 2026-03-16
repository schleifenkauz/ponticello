package ponticello.model.live

import hextant.context.Context
import javafx.application.Platform
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.div
import ponticello.impl.toDecimal
import ponticello.impl.zero
import ponticello.model.flow.AudioFlows
import ponticello.model.instr.ParameterDefObject
import ponticello.model.obj.*
import ponticello.model.player.ObjectPlaybackInfo
import ponticello.model.project.LIVE_BUFFERS
import ponticello.model.project.get
import ponticello.model.project.scripts
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectGroup
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ValueControl
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.dock.AppLayout
import ponticello.ui.score.ScoreObjectViewPane
import reaktive.value.*
import reaktive.value.binding.and
import java.util.concurrent.CompletableFuture

@Serializable
sealed class ItemTarget : AbstractContextualObject() {
    open val supportedModes: List<GridItem.Mode>
        get() = listOf(GridItem.Mode.Toggle, GridItem.Mode.Gate)

    open val targetObject: ScoreObject? get() = null

    abstract val name: ReactiveString

    @Transient
    protected lateinit var grid: LauncherGrid
        private set

    abstract val canView: Boolean

    open val canStop: Boolean get() = false

    abstract val isActive: ReactiveBoolean

    abstract fun activate(velocity: Int, mode: GridItem.Mode)

    abstract fun deactivate(mode: GridItem.Mode)

    open fun pressed(velocity: Int, item: GridItem) {
        if (!isActive.now || item.mode.now != GridItem.Mode.Toggle) {
            activate(velocity, item.mode.now)
        } else {
            deactivate(item.mode.now)
        }
    }

    open fun released(item: GridItem) {
        if (item.mode.now == GridItem.Mode.Gate) {
            deactivate(item.mode.now)
        }
    }

    fun initialize(grid: LauncherGrid) {
        initialize(grid.context)
        this.grid = grid
    }

    @Serializable
    @SerialName("None")
    class None : ItemTarget() { //has to be a class, otherwise we get double initialization errors
        override val supportedModes: List<GridItem.Mode>
            get() = listOf(GridItem.Mode.None)

        override val isActive: ReactiveBoolean
            get() = reactiveValue(false)

        override val name: ReactiveString
            get() = reactiveValue("<select>")

        override val canView: Boolean
            get() = false

        override fun activate(velocity: Int, mode: GridItem.Mode) {
        }

        override fun deactivate(mode: GridItem.Mode) {
        }

        override fun toString(): String = "None"

        override fun equals(other: Any?): Boolean = other is None

        override fun hashCode(): Int = javaClass.hashCode()
    }

    @Serializable
    @SerialName("ToggleRecording")
    data class ToggleRecording(private val liveBuffer: LiveBufferReference) : ItemTarget() {
        override val canView: Boolean
            get() = true
        override lateinit var isActive: ReactiveBoolean
            private set

        override val canStop get() = true

        override val name: ReactiveString get() = liveBuffer.name

        override fun toString(): String = "Toggle ${liveBuffer.getName()}"

        override fun initialize(context: Context) {
            super.initialize(context)
            liveBuffer.resolve(context.project[LIVE_BUFFERS])
            isActive = liveBuffer.get()?.enabled ?: reactiveValue(false)
        }

        override fun activate(velocity: Int, mode: GridItem.Mode) {
            val buf = liveBuffer.get() ?: return
            buf.setEnabled(true)
        }

        override fun deactivate(mode: GridItem.Mode) {
            val buf = liveBuffer.get() ?: return
            buf.setEnabled(false)
        }
    }

    @Serializable
    @SerialName("Object")
    data class Object(
        val ref: ScoreObjectReference,
        val yPosition: ReactiveVariable<Decimal>,
    ) : ItemTarget() {
        val velocityParameter: ReactiveVariable<String?> = reactiveVariable(null)

        @Transient
        private var instanceId: CompletableFuture<Int?>? = null

        override val canView: Boolean
            get() = ref.isResolved.now

        override val targetObject: ScoreObject?
            get() = ref.get()

        override val name: ReactiveString get() = ref.name

        override val supportedModes: List<GridItem.Mode>
            get() = GridItem.Mode.entries - GridItem.Mode.None

        override val canStop: Boolean get() = true

        @Transient
        private val active = reactiveVariable(false)

        override val isActive: ReactiveBoolean
            get() = active

        override fun initialize(context: Context) {
            super.initialize(context)
            ref.resolve(context[ScoreObjectRegistry])
        }

        override fun activate(velocity: Int, mode: GridItem.Mode) {
            active.set(true)
            val obj = ref.get() ?: return
            val player = grid.getPlayer()
            val quantization = QuantizationConfig.createDefault() //TODO where to get the quantization???
            player.getClock().scheduleAction(quantization) { quantizationDelay ->
                val time = if (player.isPlaying.now) player.playHead.currentTime else zero
                val position = ObjectPosition(time, yPosition.now)
                val totalDelay = quantizationDelay.coerceAtMost(context.playbackSettings.lookAhead)
                val extraArguments = mutableMapOf<ParameterDefObject, ParameterControl>()
                extraArguments[ParameterDefObject.VELOCITY] = ValueControl.create(velocity.toDecimal())
                if (mode != GridItem.Mode.Trigger) {
                    extraArguments[ParameterDefObject.DURATION] = ValueControl.create(Decimal.INF)
                }
                val playbackInfo = ObjectPlaybackInfo(position, player, totalDelay / 2, extraArguments = extraArguments)
                instanceId = grid.scheduler.scheduleObject(obj, playbackInfo, scLangLatency = totalDelay / 2)
            }
        }

        override fun released(item: GridItem) {
            if (item.mode.now == GridItem.Mode.Trigger) {
                active.set(false)
            }
            super.released(item)
        }

        override fun deactivate(mode: GridItem.Mode) {
            active.set(false)
            val obj = ref.get() ?: return
            instanceId?.thenAccept { id ->
                if (id != null) {
                    grid.scheduler.stopObjectInstantly(obj, id)
                }
            }
            instanceId = null
        }

        override fun toString() = ref.getName()
    }

    @Serializable
    @SerialName("LiveObject")
    data class LiveObjectRef(private val ref: ObjectReference<LiveObject>) : ItemTarget() {
        override val canView: Boolean
            get() = ref.isResolved.now

        val liveObject get() = ref.get()

        override val name: ReactiveString
            get() = ref.name

        override val targetObject: ScoreObject?
            get() = (ref.get() as? LiveScoreObject)?.scoreObject

        override val canStop: Boolean get() = true

        override val isActive: ReactiveBoolean
            get() = liveObject?.isScheduled ?: reactiveValue(false)

        override fun initialize(context: Context) {
            super.initialize(context)
            ref.resolve(context[LiveObjectRegistry])
        }

        override fun activate(velocity: Int, mode: GridItem.Mode) {
            val obj = ref.get() ?: return
            obj.play()
            if (obj is LiveScoreObject) {
                Platform.runLater {
                    context[AppLayout].get<ScoreObjectViewPane>().showContent(obj)
                }
            }
        }

        override fun deactivate(mode: GridItem.Mode) {
            val obj = ref.get() ?: return
            obj.pause()
        }

        override fun toString() = "LiveObject: ${ref.getName()}"
    }

    @Serializable
    @SerialName("Flow")
    data class Flow(val ref: FlowReference) : ItemTarget() {
        override val canView: Boolean
            get() = true

        @Transient
        override lateinit var isActive: ReactiveBoolean
            private set

        override val canStop: Boolean get() = true

        override val name: ReactiveString get() = ref.name

        override fun initialize(context: Context) {
            super.initialize(context)
            ref.resolve(context)
            val flow = ref.get()
            isActive =
                if (flow == null) reactiveValue(false)
                else flow.isActive and flow.parentGroup!!.isActive
        }

        override fun deactivate(mode: GridItem.Mode) {
            val flow = ref.get() ?: return
            flow.setActive(false)
        }

        override fun activate(velocity: Int, mode: GridItem.Mode) {
            val flow = ref.get() ?: return
            if (!flow.parentGroup!!.isActive.now) {
                flow.parentGroup!!.toggleActive()
            }
            flow.setActive(true)
        }

        override fun toString(): String = "Flow $${ref.getName()}"
    }

    @Serializable
    data class Script(val ref: ScriptObjectReference) : ItemTarget() {
        override val canView: Boolean
            get() = true

        override val name: ReactiveString get() = ref.name

        private val active = reactiveVariable(false)
        override val isActive: ReactiveBoolean get() = active

        override fun activate(velocity: Int, mode: GridItem.Mode) {
            active.set(true)
            val script = ref.get() ?: return
            script.executeContents(context[SuperColliderClient])
        }

        override fun deactivate(mode: GridItem.Mode) {
            active.set(false)
        }

        override fun toString(): String = "Script ${ref.getName()}"
    }

    companion object {
        fun options(context: Context): List<ItemTarget> {
            val objects = context[ScoreObjectRegistry].filter { obj -> obj.affectsPlayback }
            val objectTargets = objects
                .filter { obj -> obj !is ScoreObjectGroup }
                .map { obj -> Object(obj.reference(), reactiveVariable(zero(ObjectPosition.TIME_PRECISION))) }
            val liveObjects = context[LiveObjectRegistry]
            val liveObjectTargets = liveObjects.map { obj -> LiveObjectRef(obj.reference()) }
            val flowTargets = context[AudioFlows].all().flatMap { group ->
                group.flows.all().map { flow -> Flow(flow.reference()) }
            }
            val scriptTargets = context.project.scripts.map { script -> Script(script.reference()) }
            val toggleRecording = context.project[LIVE_BUFFERS].map { buf -> ToggleRecording(buf.reference()) }
            return listOf(None()) + objectTargets + liveObjectTargets + flowTargets + scriptTargets + toggleRecording
        }
    }
}