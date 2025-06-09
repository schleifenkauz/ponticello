package ponticello.model.live

import hextant.context.Context
import javafx.application.Platform
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.Settings
import ponticello.model.flow.AudioFlows
import ponticello.model.obj.*
import ponticello.model.player.Recorder
import ponticello.model.player.ScorePlayer
import ponticello.model.project.LIVE_TASKS
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
import ponticello.sc.NumericalControlSpec
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.mapOnto
import ponticello.ui.dock.AppLayout
import ponticello.ui.launcher.PonticelloMainActivity
import ponticello.ui.registry.ScoreObjectRegistryPane
import reaktive.value.*

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
    data object None : ItemTarget() {
        override val isActive: ReactiveBoolean
            get() = reactiveValue(false)

        override val canView: Boolean
            get() = false

        override fun pressed(velocity: Int, item: LauncherGrid.GridItem) {
        }

        override fun released(item: LauncherGrid.GridItem) {
        }
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
    data class Object(val ref: ScoreObjectReference) : ItemTarget() {
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
            player.getClock().scheduleAction(obj.quantizationConfig) { quantizationDelay ->
                val time = if (player.isPlaying.now) player.playHead.currentTime else zero
                val y = obj.liveConfig.yPosition.now
                val position = ObjectPosition(time, y)
                val totalDelay = quantizationDelay.coerceAtMost(context[Settings].lookAhead)
                val extraArguments = mutableMapOf<ParameterDefObject, ParameterControl>()
                val velocityParameter = velocityParameter.now.get()
                val spec = velocityParameter?.spec?.now as? NumericalControlSpec
                if (obj is ParameterizedObject && spec != null) {
                    val transform = spec.mapOnto(0.0,127.0)
                    val value = transform.unmap(velocity.toDouble()).toDecimal().withPrecision(spec.precision)
                    extraArguments[velocityParameter] = ValueControl.create(value)
                }
                ScorePlayer.execute {
                    grid.activeObjects[item] = grid.scheduler.scheduleObject(
                        obj, position, cutoff = zero, player,
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
    @SerialName("Player")
    data class Player(val ref: ScoreObjectReference) : ItemTarget() {
        override val canView: Boolean
            get() = ref.isResolved.now

        override val targetObject: ScoreObject?
            get() = ref.get()

        override val canStop: Boolean get() = true

        @Transient
        override lateinit var isActive: ReactiveBoolean

        override fun initialize(context: Context) {
            super.initialize(context)
            ref.resolve(context[ScoreObjectRegistry])
            if (context.hasProperty(PonticelloMainActivity)) {
                preparePlayer()
            }
        }

        override fun pressed(velocity: Int, item: LauncherGrid.GridItem) {
            Platform.runLater {
                val obj = ref.get() ?: return@runLater
                if (obj.player == null) {
                    context[AppLayout].get<ScoreObjectRegistryPane>().showContent(obj)
                }
                val player = obj.player
                if (player == null) {
                    Logger.warn("Player is null for ScoreObject ${obj.name.now}", Logger.Category.Playback)
                    return@runLater
                }
                if (!player.isScheduled.now) {
                    player.play()
                    context[AppLayout].get<ScoreObjectRegistryPane>().showContent(obj)
                } else if (!item.stopOnRelease.now) {
                    player.pause()
                }
            }

        }

        override fun released(item: LauncherGrid.GridItem) {
            val player = ref.get()?.player ?: return
            if (player.isScheduled.now && item.stopOnRelease.now) {
                player.pause()
            }
        }

        fun preparePlayer() {
            val obj = targetObject
            if (obj == null) {
                isActive = reactiveValue(false)
                return
            }
            val scoreObjectsPane = context[AppLayout].get<ScoreObjectRegistryPane>()
            scoreObjectsPane.listView.initializeContent(obj)
            isActive = obj.player?.isScheduled ?: reactiveValue(false)
        }

        override fun toString() = "Player ${ref.getName()}"
    }

    @Serializable
    @SerialName("Flow")
    data class Flow(val ref: AudioFlows.FlowReference) : ItemTarget() {
        override val canView: Boolean
            get() = true

        @Transient
        override lateinit var isActive: ReactiveBoolean
            private set

        override val canStop: Boolean get() = true

        override fun initialize(context: Context) {
            super.initialize(context)
            isActive = ref.getFlow(context[AudioFlows])?.isActive ?: reactiveValue(false)
        }

        override fun pressed(velocity: Int, item: LauncherGrid.GridItem) {
            val flow = ref.getFlow(context[AudioFlows]) ?: return
            if (!flow.isActive.now) flow.setActive(true)
            else if (!item.stopOnRelease.now) {
                flow.setActive(false)
            }
        }

        override fun released(item: LauncherGrid.GridItem) {
            val flow = ref.getFlow(context[AudioFlows]) ?: return
            if (flow.isActive.now && item.stopOnRelease.now) {
                flow.setActive(false)
            }
        }

        override fun toString(): String = "Flow ${ref.flowName}"
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

    @Serializable
    data class LiveTask(val ref: LiveTaskReference) : ItemTarget() {
        override val canView: Boolean
            get() = true

        override val isActive: ReactiveBoolean
            get() = ref.get()?.isActive ?: reactiveValue(false)

        override val canStop: Boolean get() = true

        override fun pressed(velocity: Int, item: LauncherGrid.GridItem) {
            val obj = ref.get() ?: return
            if (!obj.isActive.now) obj.toggleActive()
            else if (!item.stopOnRelease.now) {
                obj.toggleActive()
            }
        }

        override fun released(item: LauncherGrid.GridItem) {
            val obj = ref.get() ?: return
            if (obj.isActive.now && item.stopOnRelease.now) {
                obj.toggleActive()
            }
        }

        override fun toString(): String = "Task: ${ref.getName()}"
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
            val scriptTargets = context.project.scripts.map { script -> Script(script.reference()) }
            val taskTargets = context.project[LIVE_TASKS].map { task -> LiveTask(task.reference()) }
            val defaultOptions = listOf(None, ToggleRecording)
            return defaultOptions + objectTargets + playerTargets + flowTargets + scriptTargets + taskTargets
        }
    }
}