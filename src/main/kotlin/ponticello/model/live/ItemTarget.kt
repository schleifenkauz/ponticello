package ponticello.model.live

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.zero
import ponticello.model.flow.AudioFlows
import ponticello.model.midi.MidiGridInstrument
import ponticello.model.obj.*
import ponticello.model.player.ScorePlayer
import ponticello.model.project.LIVE_BUFFERS
import ponticello.model.project.get
import ponticello.model.project.objects
import ponticello.model.project.scripts
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectGroup
import ponticello.sc.client.ScWriter
import ponticello.ui.actions.PlaybackActions
import reaktive.value.*
import reaktive.value.binding.and
import reaktive.value.binding.`if`

@Serializable
sealed class ItemTarget : AbstractContextualObject() {
    open val supportedModes: List<GridItem.Mode>
        get() = listOf(GridItem.Mode.Toggle, GridItem.Mode.Gate)

    open val targetObject: ScoreObject? get() = null

    abstract val name: ReactiveString

    @Transient
    protected lateinit var grid: MidiGridInstrument
        private set

    abstract val canView: Boolean

    abstract val isActive: ReactiveBoolean

    abstract fun copy(): ItemTarget

    abstract fun ScWriter.code()

    fun initialize(grid: MidiGridInstrument) {
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
            get() = reactiveValue("")

        override val canView: Boolean
            get() = false

        override fun copy(): ItemTarget = None()

        override fun ScWriter.code() {
            append("nil")
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

        override val name: ReactiveString get() = liveBuffer.name

        override fun toString(): String = "Toggle ${liveBuffer.getName()}"

        override fun initialize(context: Context) {
            super.initialize(context)
            liveBuffer.resolve(context.project[LIVE_BUFFERS])
            isActive = liveBuffer.get()?.enabled ?: reactiveValue(false)
        }

        override fun copy(): ItemTarget = ToggleRecording(liveBuffer)

        override fun ScWriter.code() {
            val buf = liveBuffer.get() ?: return append("nil")
            val id = context.project[LIVE_BUFFERS].ids.getId(buf) ?: return append("nil")
            append("ToggleRecordingItem($id)")
        }
    }

    @Serializable
    @SerialName("Object")
    data class Object(
        val ref: ScoreObjectReference,
        val yPosition: ReactiveVariable<Decimal>,
    ) : ItemTarget() {
        override val canView: Boolean
            get() = ref.isResolved.now

        override val targetObject: ScoreObject?
            get() = ref.get()

        override val name: ReactiveString get() = ref.name

        override val supportedModes: List<GridItem.Mode>
            get() = GridItem.Mode.entries - GridItem.Mode.None

        @Transient
        private val active = reactiveVariable(false)

        override val isActive: ReactiveBoolean
            get() = active

        override fun initialize(context: Context) {
            super.initialize(context)
            ref.resolve(context.project.objects)
        }

        override fun copy(): ItemTarget = Object(ref, yPosition)

        override fun ScWriter.code() {
            append("SoundProcessGridItem('${ref.name.now}')")
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

        override val isActive: ReactiveBoolean
            get() = liveObject?.isScheduled ?: reactiveValue(false)

        override fun initialize(context: Context) {
            super.initialize(context)
            ref.resolve(context[LiveObjectRegistry])
        }

        override fun copy(): ItemTarget = LiveObjectRef(ref)

        override fun ScWriter.code() {
            val obj = ref.get() ?: return append("nil")
            val id = context[LiveObjectRegistry].ids.getId(obj) ?: return append("nil")
            append("LiveObjectGridItem($id)")
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

        override val name: ReactiveString get() = ref.name

        override fun initialize(context: Context) {
            super.initialize(context)
            ref.resolve(context)
            val flow = ref.get()
            isActive =
                if (flow == null) reactiveValue(false)
                else flow.isActive and flow.parentGroup!!.isActive
        }

        override fun copy(): ItemTarget = Flow(ref)

        override fun ScWriter.code() {
            val obj = ref.get() ?: return append("nil")
            val id = context[AudioFlows].ids.getId(obj) ?: return append("nil")
            append("FlowGridItem($id)")
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

        override val supportedModes: List<GridItem.Mode>
            get() = listOf(GridItem.Mode.Trigger)

        override fun initialize(context: Context) {
            super.initialize(context)
            ref.resolve(context.project.scripts)
        }

        override fun copy(): ItemTarget = Script(ref)

        override fun ScWriter.code() {
            val script = ref.get() ?: return append("nil")
            val id = context.project.scripts.ids.getId(script) ?: return append("nil")
            append("ScriptGridItem($id)")
        }

        override fun toString(): String = "Script ${ref.getName()}"
    }

    @Serializable
    data class PlaybackAction(@SerialName("_type") val type: PlaybackActions.Type) : ItemTarget() {
        override lateinit var name: ReactiveString
        override val canView: Boolean
            get() = false
        override val isActive: ReactiveBoolean
            get() = when (type) {
                PlaybackActions.Type.Play -> context[ScorePlayer.MAIN].isPlaying
                else -> reactiveValue(false)
            }

        override fun initialize(context: Context) {
            super.initialize(context)
            name = when (type) {
                PlaybackActions.Type.Play -> `if`(
                    context[ScorePlayer.MAIN].isPlaying,
                    then = { "pause" },
                    otherwise = { "play" }
                )

                PlaybackActions.Type.Stop -> reactiveValue("stop")
                PlaybackActions.Type.GoToStart -> reactiveValue("->start")
            }
        }

        override fun copy(): ItemTarget = PlaybackAction(type)

        override fun ScWriter.code() {
            append("PlaybackActionItem(\\${type.name.lowercase()})")
        }
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