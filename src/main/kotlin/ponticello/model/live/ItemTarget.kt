package ponticello.model.live

import hextant.context.Context
import javafx.application.Platform
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.GlobalSettings
import ponticello.model.flow.AudioFlows
import ponticello.model.obj.*
import ponticello.model.player.Recorder
import ponticello.model.player.ScorePlayer
import ponticello.model.project.scripts
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.registry.reference
import ponticello.model.score.ObjectPosition
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectGroup
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ValueControl
import ponticello.sc.NumericalControlSpec
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.mapOnto
import ponticello.ui.dock.AppLayout
import ponticello.ui.score.ScoreObjectViewPane
import reaktive.value.*
import reaktive.value.binding.and
import kotlin.collections.set

@Serializable
sealed class ItemTarget : AbstractContextualObject() {
    open val targetObject: ScoreObject? get() = null

    @Transient
    protected lateinit var grid: LauncherGrid
        private set

    abstract val canView: Boolean

    open val canStop: Boolean get() = false

    abstract val isActive: ReactiveBoolean

    abstract fun pressed(velocity: Int, item: LauncherGrid.GridItem)

    abstract fun released(item: LauncherGrid.GridItem)

    fun initialize(grid: LauncherGrid) {
        initialize(grid.context)
        this.grid = grid
    }

    @Serializable
    @SerialName("None")
    class None : ItemTarget() { //has to be a class, otherwise we get double initialization errors
        override val isActive: ReactiveBoolean
            get() = reactiveValue(false)

        override val canView: Boolean
            get() = false

        override fun pressed(velocity: Int, item: LauncherGrid.GridItem) {
        }

        override fun released(item: LauncherGrid.GridItem) {
        }

        override fun toString(): String = "None"

        override fun equals(other: Any?): Boolean = other is None

        override fun hashCode(): Int = javaClass.hashCode()
    }

    @Serializable
    @SerialName("ToggleRecording")
    data object ToggleRecording : ItemTarget() {
        override val canView: Boolean
            get() = false
        override lateinit var isActive: ReactiveBoolean
            private set

        override val canStop get() = true

        override fun toString(): String = "Toggle Recording"

        override fun initialize(context: Context) {
            super.initialize(context)
            isActive = context[Recorder].isActive
        }

        override fun pressed(velocity: Int, item: LauncherGrid.GridItem) {
            context[Recorder].toggle()
        }

        override fun released(item: LauncherGrid.GridItem) {
            val recorder = context[Recorder]
            if (item.stopOnRelease.now && recorder.isActive.now) {
                recorder.stopRecording()
            }
        }
    }

    @Serializable
    @SerialName("Object")
    data class Object(
        val ref: ScoreObjectReference,
        val yPosition: ReactiveVariable<Decimal>,
    ) : ItemTarget() {
        var velocityParameter: ReactiveVariable<ParameterDefReference> = reactiveVariable(ObjectReference.none())

        override val canView: Boolean
            get() = ref.isResolved.now

        override val targetObject: ScoreObject?
            get() = ref.get()

        override val canStop: Boolean get() = true

        @Transient
        private val active = reactiveVariable(false)

        override val isActive: ReactiveBoolean
            get() = active

        override fun initialize(context: Context) {
            super.initialize(context)
            val obj = ref.resolve(context[ScoreObjectRegistry])
            val velocityParam = velocityParameter.now
            if (obj is ParameterizedObject) {
                val paramName = velocityParam.getName()
                velocityParameter.now = obj.def.getParameter(paramName)?.reference()
                    ?: velocityParam.also { it.setUnresolved() }
            } else {
                velocityParameter.now.setUnresolved()
            }
        }

        override fun pressed(velocity: Int, item: LauncherGrid.GridItem) {
            active.set(true)
            val obj = ref.get() ?: return
            val player = grid.getPlayer()
            val quantization = QuantizationConfig.createDefault() //TODO where to get the quantization???
            player.getClock().scheduleAction(quantization) { quantizationDelay ->
                val time = if (player.isPlaying.now) player.playHead.currentTime else zero
                val position = ObjectPosition(time, yPosition.now)
                val totalDelay = quantizationDelay.coerceAtMost(context[GlobalSettings].lookAhead)
                val extraArguments = mutableMapOf<ParameterDefObject, ParameterControl>()
                val velocityParameter = velocityParameter.now.get()
                val spec = velocityParameter?.spec?.now as? NumericalControlSpec
                if (obj is ParameterizedObject && spec != null) {
                    val transform = spec.mapOnto(0.0, 127.0)
                    val value = transform.unmap(velocity.toDouble()).toDecimal().withPrecision(spec.precision)
                    extraArguments[velocityParameter] = ValueControl.create(value)
                }
                ScorePlayer.execute {
                    grid.activeObjects[item] = grid.scheduler.scheduleObject(
                        obj, instance = null, position, cutoff = zero, player,
                        scLangLatency = totalDelay / 2, serverLatency = totalDelay / 2,
                        extraArguments
                    )
                }
            }

        }

        override fun released(item: LauncherGrid.GridItem) {
            active.set(false)
            val activeObject = grid.activeObjects[item] ?: return
            grid.addToTargetScore(activeObject, item)
            if (!item.stopOnRelease.now) return
            ScorePlayer.execute {
                grid.scheduler.stopObjectInstantly(activeObject)
            }
            grid.activeObjects[item] = null
        }

        override fun toString() = ref.getName()
    }

    @Serializable
    @SerialName("LiveObject")
    data class LiveObjectRef(private val ref: ObjectReference<LiveObject>) : ItemTarget() {
        override val canView: Boolean
            get() = ref.isResolved.now

        val liveObject get() = ref.get()

        override val targetObject: ScoreObject?
            get() = (ref.get() as? LiveScoreObject)?.scoreObject

        override val canStop: Boolean get() = true

        override val isActive: ReactiveBoolean
            get() = liveObject?.isScheduled ?: reactiveValue(false)

        override fun initialize(context: Context) {
            super.initialize(context)
            ref.resolve(context[LiveObjectRegistry])
        }

        override fun pressed(velocity: Int, item: LauncherGrid.GridItem) {
            Platform.runLater {
                val obj = ref.get() ?: return@runLater
                if (!obj.isScheduled.now) {
                    obj.play()
                    if (obj is LiveScoreObject) {
                        context[AppLayout].get<ScoreObjectViewPane>().showContent(obj)
                    }
                } else if (!item.stopOnRelease.now) {
                    obj.pause()
                }
            }

        }

        override fun released(item: LauncherGrid.GridItem) {
            val obj = ref.get() ?: return
            if (obj.isScheduled.now && item.stopOnRelease.now) {
                obj.pause()
            }
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

        override fun initialize(context: Context) {
            super.initialize(context)
            ref.resolve(context)
            val flow = ref.get()
            isActive =
                if (flow == null) reactiveValue(false)
                else flow.isActive and flow.parentGroup!!.isActive
        }

        override fun pressed(velocity: Int, item: LauncherGrid.GridItem) {
            val flow = ref.get() ?: return
            if (!flow.parentGroup!!.isActive.now) {
                flow.parentGroup!!.toggleActive()
            }
            if (!flow.isActive.now) flow.setActive(true)
            else if (!item.stopOnRelease.now) {
                flow.setActive(false)
            }
        }

        override fun released(item: LauncherGrid.GridItem) {
            val flow = ref.get() ?: return
            if (flow.isActive.now && item.stopOnRelease.now) {
                flow.setActive(false)
            }
        }

        override fun toString(): String = "Flow ${ref.getName()}"
    }

    @Serializable
    data class Script(val ref: ScriptObjectReference) : ItemTarget() {
        override val canView: Boolean
            get() = true

        override val isActive: ReactiveBoolean
            get() = reactiveValue(false)

        override fun pressed(velocity: Int, item: LauncherGrid.GridItem) {
            val script = ref.get() ?: return
            script.executeContents(context[SuperColliderClient])
        }

        override fun released(item: LauncherGrid.GridItem) {

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
            val defaultOptions = listOf(None(), ToggleRecording)
            return defaultOptions + objectTargets + liveObjectTargets + flowTargets + scriptTargets
        }
    }
}