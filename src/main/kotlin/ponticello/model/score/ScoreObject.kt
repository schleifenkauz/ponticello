package ponticello.model.score

import fxutils.asHorizontalDirection
import fxutils.drag.TypedDataFormat
import fxutils.prompt.YesNoPrompt
import fxutils.undo.AbstractEdit
import fxutils.undo.Edit
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import hextant.context.Context
import hextant.context.withoutUndo
import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.Side
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.flow.NodePlacement
import ponticello.model.live.LiveConfig
import ponticello.model.live.QuantizationConfig
import ponticello.model.obj.*
import ponticello.model.player.ScorePlayer
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.Score.Companion.rootScore
import ponticello.model.score.controls.EnvelopeControl
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
sealed class ScoreObject : AbstractRenamableObject() {
    abstract val type: String
    open val canMute: Boolean get() = true
    private val _associatedColor: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color?> =
        reactiveVariable(null)

    open val associatedColor: ReactiveValue<Color?> get() = _associatedColor

    open val associatedControls: Map<String, ParameterControl> get() = emptyMap()

    open val canResizeHorizontally: Boolean get() = true
    open val canResizeVertically: Boolean get() = true

    open val affectsPlayback: Boolean get() = true

    @Transient
    var player: ScorePlayer? = null

    @SerialName("duration")
    private var _duration = reactiveVariable(0.0.asTime)

    @SerialName("height")
    private var _height = reactiveVariable(0.0.asY)

    var duration: Decimal
        get() = _duration.now
        protected set(value) {
            _duration.now = value
            quantizationConfig.setDuration(value)
        }

    var height: Decimal
        get() = _height.now
        protected set(value) {
            _height.now = value
        }

    val quantizationConfig: QuantizationConfig = QuantizationConfig.createDefault()

    val liveConfig = LiveConfig.createDefault()

    @Transient
    private val viewManager: ListenerManager<Listener> = ListenerManager.createWeakListenerManager()

    @Transient
    var durationBeforeResize: Decimal = zero(ObjectPosition.TIME_PRECISION)
        private set

    @Transient
    var heightBeforeResize: Decimal = zero(ObjectPosition.Y_PRECISION)
        private set

    @Transient
    protected var resizeSide: Side? = null

    @Transient
    protected var resizeMode: ResizeMode? = null

    val isResizing get() = resizeMode != null

    override val registry: ScoreObjectRegistry
        get() = context[ScoreObjectRegistry]

    open val superColliderPrefix: String? get() = null

    open fun duration(): ReactiveValue<Decimal> = _duration

    fun height(): ReactiveValue<Decimal> = _height

    override fun initialize(context: Context) {
        super.initialize(context)
        quantizationConfig.initialize(context, this)
    }

    open fun validate(): Boolean {
        return true
    }

    fun recolor(newColor: Color?) {
        VariableEdit.updateVariable(_associatedColor, newColor, context[UndoManager], "Recolor object")
    }

    abstract fun writeCode(
        uniqueName: String,
        placement: NodePlacement?,
        cutoff: Decimal,
        latency: Decimal,
        extraArguments: Map<ParameterDefObject, ParameterControl> = emptyMap(),
    ): String

    protected fun recordEdit(edit: Edit) {
        if (initialized) {
            context[UndoManager].record(edit)
        }
    }

    override fun canRenameTo(newName: String): Boolean = !context[ScoreObjectRegistry].has(newName)

    fun setInitialSize(duration: Decimal, height: Decimal) {
        this.duration = duration
        this.height = height
    }

    open fun beginResize(mode: ResizeMode, side: Side): Boolean {
        durationBeforeResize = duration
        heightBeforeResize = height
        resizeMode = mode
        resizeSide = side
        return mode != ResizeMode.DeepStretch
    }

    open fun resize(targetDuration: Decimal, targetHeight: Decimal) {
        val mode = resizeMode ?: error("resizeMode not set")
        val side = resizeSide ?: error("resizeSide not set")
        when (mode) {
            ResizeMode.Regular -> {
                for ((parameter, ctrl) in associatedControls) {
                    if (ctrl !is EnvelopeControl) continue
                    val spec = getSpec(parameter) as? NumericalControlSpec
                    if (spec != null && resizeSide in setOf(Side.LEFT, Side.RIGHT)) {
                        ctrl.points.resize(targetDuration, side.asHorizontalDirection(), spec)
                    }
                }
            }

            ResizeMode.Stretch, ResizeMode.DeepStretch -> {
                for ((_, ctrl) in associatedControls) {
                    if (ctrl is EnvelopeControl) {
                        ctrl.points.rescale(targetDuration)
                    }
                }
            }
        }
        val deltaDur = targetDuration - duration
        val deltaHeight = targetHeight - height
        this.duration = targetDuration
        this.height = targetHeight
        val instances = context[rootScore].instancesOf(this).toSet()
        for (inst in instances) {
            if (resizeSide == Side.LEFT) {
                inst.moveTo(inst.start - deltaDur, inst.y, simpleMove = false)
            }
            if (resizeSide == Side.TOP) {
                inst.moveTo(inst.start, inst.y - deltaHeight, simpleMove = false)
            }
        }
        viewManager.notifyListeners { resizedObject(this@ScoreObject) }
    }

    open fun finishResize(recordEdit: Boolean = true) {
        val mode = resizeMode ?: error("resizeMode not set")
        val side = resizeSide ?: error("resizeSide not set")
        if (duration == durationBeforeResize && height == heightBeforeResize) return
        val deltaDuration = duration - durationBeforeResize
        val deltaHeight = height - heightBeforeResize
        viewManager.notifyListeners {
            finishedResize(this@ScoreObject, deltaDuration, deltaHeight, side)
        }
        for (ctrl in associatedControls.values) {
            if (ctrl is EnvelopeControl) {
                ctrl.points.finishedResize()
            }
        }
        if (recordEdit) {
            context[UndoManager].record(
                ResizeEdit(
                    this, durationBeforeResize, heightBeforeResize, this.duration, this.height,
                    mode, side
                )
            )
        }
    }

    fun resize(targetDuration: Decimal, targetHeight: Decimal, type: ResizeMode, side: Side) {
        beginResize(type, side)
        resize(targetDuration, targetHeight)
        finishResize()
    }

    protected abstract fun doClone(): ScoreObject

    fun clone(newName: String): ScoreObject {
        val obj = doClone()
        obj.duration = duration
        obj.height = height
        obj._associatedColor.now = associatedColor.now
        obj.setInitialName(newName)
        return obj
    }

    protected open fun doCut(position: Decimal, whichHalf: HorizontalDirection): ScoreObject? {
        val clone = doClone()
        val dur = if (whichHalf == LEFT) position else duration - position
        clone.resize(dur, height, ResizeMode.Regular, side = Side.RIGHT)
        return clone
    }

    fun cut(position: Decimal, whichHalf: HorizontalDirection, newName: String): ScoreObject? {
        val obj = doCut(position, whichHalf) ?: return null
        obj.height = height
        obj._associatedColor.now = associatedColor.now
        if (whichHalf == LEFT) {
            obj.duration = position
        } else {
            obj.duration = duration - position
        }
        return obj.withName(newName)
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
        if (this !is UnresolvedScoreObject && !registry.has(this)) {
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
        if (!context.project.hasInstancesOf(this) && registry.has(this)) {
            val remove = this is MemoObject || option == Score.RegistryOption.REMOVE_WITHOUT_ASKING || YesNoPrompt(
                "Score has no instances of $this anymore. Remove it from the registry?",
                cancellable = false,
                default = true
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

        fun finishedResize(obj: ScoreObject, deltaDuration: Decimal, deltaHeight: Decimal, side: Side) {}

        fun updateIsSomeInstanceSelected(yesOrNo: Boolean) {}
    }

    private class ResizeEdit(
        private val obj: ScoreObject,
        private val oldDuration: Decimal,
        private val oldHeight: Decimal,
        private val newDuration: Decimal,
        private val newHeight: Decimal,
        private val type: ResizeMode,
        private val side: Side,
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Resize object"

        override fun doUndo() {
            obj.resize(oldDuration, oldHeight, type, side)
        }

        override fun doRedo() {
            obj.resize(newDuration, newHeight, type, side)
        }

        override fun mergeWith(other: Edit): Edit? {
            return when {
                other !is ResizeEdit -> null
                other.obj != this.obj -> null
                other.type != this.type -> null
                other.side != this.side -> null
                this.newDuration != other.oldDuration -> null
                this.newHeight != other.oldHeight -> null
                else -> ResizeEdit(
                    obj, oldDuration, oldHeight, other.newDuration, other.newHeight,
                    type, side
                )
            }
        }
    }

    enum class ResizeMode {
        Regular, Stretch, DeepStretch;

        val isStretch: Boolean get() = this == Stretch || this == DeepStretch
    }

    companion object {
        val DATA_FORMAT = TypedDataFormat<ScoreObjectReference>("score-object")
    }

}