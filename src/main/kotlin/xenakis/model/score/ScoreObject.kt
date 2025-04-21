package xenakis.model.score

import fxutils.prompt.YesNoPrompt
import hextant.context.withoutUndo
import hextant.core.editor.ListenerManager
import hextant.undo.AbstractEdit
import hextant.undo.Edit
import hextant.undo.UndoManager
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.scene.input.DataFormat
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.flow.NodePlacement
import xenakis.model.obj.AbstractRenamableObject
import xenakis.model.player.ActiveObjectManager
import xenakis.model.player.ActiveScoreObject
import xenakis.model.player.PlaybackManager
import xenakis.model.project.score
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.Score.Companion.rootScore
import xenakis.model.score.controls.EnvelopeControl
import xenakis.model.score.controls.ParameterControl
import xenakis.sc.ControlSpec
import xenakis.sc.NumericalControlSpec
import xenakis.sc.client.SuperColliderContext
import xenakis.ui.impl.Direction
import xenakis.ui.launcher.XenakisApp.Companion.primaryStage
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject

@Serializable
sealed class ScoreObject : AbstractRenamableObject() {
    abstract val type: String
    open val canMute: Boolean get() = true
    val associatedColor: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color?> = reactiveVariable(null)

    open val associatedControls: Map<String, ParameterControl> get() = emptyMap()

    open val canResize: Boolean get() = true

    open val affectsPlayback: Boolean get() = true

    @SerialName("duration")
    private var _duration = reactiveVariable(0.0.asTime)

    @SerialName("height")
    private var _height = reactiveVariable(0.0.asY)

    var duration: Decimal
        get() = _duration.now
        protected set(value) {
            _duration.now = value
        }

    var height: Decimal
        get() = _height.now
        protected set(value) {
            _height.now = value
        }

    fun duration(): ReactiveValue<Decimal> = _duration

    fun height(): ReactiveValue<Decimal> = _height

    @Transient
    private val viewManager: ListenerManager<Listener> = ListenerManager.createWeakListenerManager()

    @Transient
    var durationBeforeResize: Decimal = zero(ObjectPosition.TIME_PRECISION)
        private set

    @Transient
    var heightBeforeResize: Decimal = zero(ObjectPosition.Y_PRECISION)
        private set

    @Transient
    protected lateinit var resizeDirection: Direction

    @Transient
    protected var resizeMode: ResizeMode = ResizeMode.Regular

    override val registry: ScoreObjectRegistry
        get() = context[ScoreObjectRegistry]

    init {
        //this is only for needed when opening projects that were created before the decimal-precision update
        duration = duration.withPrecision(ObjectPosition.TIME_PRECISION)
        height = height.withPrecision(ObjectPosition.Y_PRECISION)
    }

    open val superColliderPrefix: String? get() = null

    fun activeObjects(): List<ActiveScoreObject> {
        return context[PlaybackManager].activeObjects.activeInstances(this)
    }

    fun superColliderName(suffix: Int): String {
        val prefix = superColliderPrefix ?: error("$this has no superColliderPrefix")
        return "${prefix}_${ActiveObjectManager.uniqueName(name.now, suffix)}"
    }

    open fun validate(): Boolean {
        return true
    }

    abstract fun writeCode(uniqueName: String, placement: NodePlacement?, cutoff: Decimal): String

    protected fun recordEdit(edit: Edit) {
        if (initialized) {
            context[UndoManager].record(edit)
        }
    }

    override fun canRenameTo(newName: String): Boolean = !context[ScoreObjectRegistry].has(newName)

    override fun rename(newName: String) {
        if (name.now == newName) return
        if (initialized) recordEdit(ScoreObjectEdit.Rename(oldName = name.now, newName = newName, this))
        super.rename(newName)
    }

    open fun serverBooted(context: SuperColliderContext) {}

    fun setInitialSize(duration: Decimal, height: Decimal) {
        this.duration = duration
        this.height = height
    }

    open fun beginResize(mode: ResizeMode, direction: Direction): Boolean {
        durationBeforeResize = duration
        heightBeforeResize = height
        resizeMode = mode
        resizeDirection = direction
        return mode != ResizeMode.DeepStretch
    }

    open fun resize(targetDuration: Decimal, targetHeight: Decimal) {
        when (resizeMode) {
            ResizeMode.Regular -> {
                for ((parameter, ctrl) in associatedControls) {
                    if (ctrl !is EnvelopeControl) continue
                    val spec = getSpec(parameter) as? NumericalControlSpec
                    if (spec != null && resizeDirection.horizontal != null) {
                        ctrl.points.resize(targetDuration, resizeDirection.horizontal!!, spec)
                    }
                }
            }

            ResizeMode.Stretch, ResizeMode.DeepStretch -> {
                for ((_, ctrl) in associatedControls) {
                    if (ctrl !is EnvelopeControl) continue
                    ctrl.points.rescale(targetDuration)
                }
            }
        }
        val deltaDur = targetDuration - duration
        val deltaHeight = targetHeight - height
        this.duration = targetDuration
        this.height = targetHeight
        for (inst in context[rootScore].instancesOf(this)) {
            val instTime = if (resizeDirection.left) inst.start - deltaDur else inst.start
            val instY = if (resizeDirection.up) inst.y - deltaHeight else inst.y
            inst.moveTo(instTime, instY, simpleMove = false)
        }
        viewManager.notifyListeners { resizedObject(this@ScoreObject) }
    }

    open fun finishResize(recordEdit: Boolean = true) {
        if (duration == durationBeforeResize && height == heightBeforeResize) return
        val deltaDuration = duration - durationBeforeResize
        val deltaHeight = height - heightBeforeResize
        viewManager.notifyListeners {
            finishedResize(this@ScoreObject, deltaDuration, deltaHeight, resizeDirection)
        }
        if (recordEdit) {
            context[UndoManager].record(
                ResizeEdit(
                    this, durationBeforeResize, heightBeforeResize, this.duration, this.height,
                    resizeMode, resizeDirection
                )
            )
        }
    }

    fun resize(targetDuration: Decimal, targetHeight: Decimal, type: ResizeMode, direction: Direction) {
        beginResize(type, direction)
        resize(targetDuration, targetHeight)
        finishResize()
    }

    protected abstract fun doClone(newName: String): ScoreObject

    fun clone(newName: String): ScoreObject {
        val obj = doClone(newName)
        obj.duration = duration
        obj.height = height
        obj.associatedColor.now = associatedColor.now
        return obj
    }

    protected open fun doCut(position: Decimal, whichHalf: HorizontalDirection, newName: String): ScoreObject? {
        val clone = clone(newName)
        val dur = if (whichHalf == LEFT) position else duration - position
        clone.resize(dur, height, ResizeMode.Regular, direction = Direction.NONE)
        return clone
    }

    fun cut(position: Decimal, whichHalf: HorizontalDirection, newName: String): ScoreObject? {
        val obj = doCut(position, whichHalf, newName) ?: return null
        obj.rename(newName)
        obj.height = height
        obj.associatedColor.now = associatedColor.now
        if (whichHalf == LEFT) {
            obj.duration = position
        } else {
            obj.duration = duration - position
        }
        obj.initialize(context)
        return obj
    }

    open fun getSpec(parameter: String): ControlSpec? =
        throw NoSuchElementException("no spec for parameter $parameter in $this")

    fun notifyListeners(action: Listener.() -> Unit) {
        viewManager.notifyListeners(action)
    }

    @JvmName("notifyListenersGeneric")
    inline fun <reified L : Listener> notifyListeners(crossinline action: L.() -> Unit) {
        notifyListeners { if (this is L) action() }
    }

    open fun addListener(view: Listener) {
        viewManager.addListener(view)
    }

    fun removeListener(listener: Listener) {
        viewManager.removeListener(listener)
    }

    fun addedToScore(registry: ScoreObjectRegistry) {
        if (!registry.has(this)) {
            registry.context.withoutUndo { registry.add(this) }
        }
        if (this is ScoreObjectGroup) {
            for (inst in score.objectInstances) {
                inst.obj.addedToScore(registry)
            }
        }
    }

    fun removedFromScore(option: Score.RegistryOption) {
        if (option == Score.RegistryOption.KEEP_IN_REGISTRY) return
        if (!context[currentProject].score.hasInstancesOf(this) && registry.has(this)) {
            val remove = option == Score.RegistryOption.REMOVE_WITHOUT_ASKING || YesNoPrompt(
                "Score has no instances of $this anymore. Remove it from the registry?",
                cancellable = false,
                default = false
            ).showDialog(owner = context[primaryStage]) ?: false
            if (!remove) return
            context.withoutUndo { registry.remove(this) }
            if (this is ScoreObjectGroup) {
                for (subInst in score.objectInstances) {
                    subInst.obj.removedFromScore(option)
                }
            }
        }
    }

    interface Listener {
        fun resizedObject(obj: ScoreObject) {}

        fun finishedResize(obj: ScoreObject, deltaDuration: Decimal, deltaHeight: Decimal, direction: Direction) {}

        fun isSomeInstanceSelected(yesOrNo: Boolean) {}
    }

    private class ResizeEdit(
        private val obj: ScoreObject,
        private val oldDuration: Decimal,
        private val oldHeight: Decimal,
        private val newDuration: Decimal,
        private val newHeight: Decimal,
        private val type: ResizeMode,
        private val direction: Direction,
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Resize object"

        override fun doUndo() {
            obj.resize(oldDuration, oldHeight, type, direction)
        }

        override fun doRedo() {
            obj.resize(newDuration, newHeight, type, direction)
        }

        override fun mergeWith(other: Edit): Edit? {
            return when {
                other !is ResizeEdit -> null
                other.obj != this.obj -> null
                other.type != this.type -> null
                other.direction != this.direction -> null
                this.newDuration != other.oldDuration -> null
                this.newHeight != other.oldHeight -> null
                else -> ResizeEdit(
                    obj, oldDuration, oldHeight, other.newDuration, other.newHeight,
                    type, direction
                )
            }
        }
    }

    enum class ResizeMode {
        Regular, Stretch, DeepStretch;

        val isStretch: Boolean get() = this == Stretch || this == DeepStretch
    }

    companion object {
        val DATA_FORMAT = DataFormat("score-object")
    }

    @Serializable
    class Unresolved : ScoreObject() {
        override val mutableName: ReactiveVariable<String> = reactiveVariable("<unresolved>")

        override val type: String
            get() = "none"

        override val affectsPlayback: Boolean
            get() = false

        override fun writeCode(uniqueName: String, placement: NodePlacement?, cutoff: Decimal): String = ""

        override fun doClone(newName: String): ScoreObject = this
    }
}