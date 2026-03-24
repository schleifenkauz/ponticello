package ponticello.model.score

import fxutils.drag.TypedDataFormat
import fxutils.prompt.YesNoPrompt
import fxutils.undo.AbstractEdit
import fxutils.undo.Edit
import fxutils.undo.UndoManager
import fxutils.undo.VariableEdit
import hextant.context.withoutUndo
import hextant.core.editor.ListenerManager
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.Side
import javafx.scene.input.DataFormat
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.*
import ponticello.model.obj.*
import ponticello.model.player.ObjectPlaybackInfo
import ponticello.model.registry.ScoreObjectRegistry
import ponticello.model.score.controls.EnvelopeControl
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.client.ScWriter
import ponticello.sc.client.run
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import reaktive.value.*

@Serializable
sealed class ScoreObject : AbstractSuperColliderObject() {
    abstract val type: String
    open val canMute: Boolean get() = true
    open val canDuplicate: Boolean get() = false
    private val _associatedColor: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color?> =
        reactiveVariable(null)

    open val associatedColor: ReactiveValue<Color?> get() = _associatedColor

    open val associatedControls: Map<String, ParameterControl> get() = emptyMap()

    open val canResizeHorizontally: Boolean get() = true
    open val canResizeVertically: Boolean get() = true

    open val askBeforeDeleting: Boolean get() = true

    open val affectsPlayback: Boolean get() = true

    @SerialName("duration")
    private var _duration = reactiveVariable(0.0.asTime)

    @SerialName("height")
    private var _height = reactiveVariable(0.0.asY)

    open var duration: Decimal
        get() = _duration.now
        protected set(value) {
            _duration.now = value
        }

    open var height: Decimal
        get() = _height.now
        protected set(value) {
            _height.now = value
        }

    @SerialName("text")
    val memoText: ReactiveVariable<String> = reactiveVariable("")

    @Transient
    var isCreatedInSuperCollider = false
        protected set

    @Transient
    private val viewManager: ListenerManager<Listener> = ListenerManager.createWeakListenerManager()

    @Transient
    var durationBeforeResize: Decimal = zero(ObjectPosition.TIME_PRECISION)
        private set

    @Transient
    var heightBeforeResize: Decimal = zero(ObjectPosition.Y_PRECISION)
        private set

    @Transient
    private var envelopesBeforeResize: Map<String, Envelope> = emptyMap()

    @Transient
    protected var resizeSide: Side? = null

    @Transient
    protected var resizeMode: ResizeMode? = null

    @Transient
    private val instances = reactiveVariable(0)

    val numberOfInstances: ReactiveInt get() = instances

    val isResizing get() = resizeMode != null

    override val registry: ScoreObjectRegistry
        get() = context[ScoreObjectRegistry]

    open fun duration(): ReactiveValue<Decimal> = _duration

    fun height(): ReactiveValue<Decimal> = _height

    open val minY: Decimal get() = zero
    open val maxY: Decimal get() = _height.now

    open fun validate(): Boolean {
        return true
    }

    fun recolor(newColor: Color?) {
        VariableEdit.updateVariable(_associatedColor, newColor, context[UndoManager], "Recolor object")
    }

    override fun superColliderName(objectName: String): String = "~obj_$objectName"

    final override fun ScWriter.createObject() {
        if (affectsPlayback) {
            isCreatedInSuperCollider = false
        }
    }

    protected open fun createInSuperCollider(writer: ScWriter) {
        if (affectsPlayback) {
            Logger.warn("createInSuperCollider not implemented for $writer", Logger.Category.Playback)
        }
    }

    fun createInSuperCollider() {
        client.run {
            createInSuperCollider(this)
        }
        isCreatedInSuperCollider = true
    }

    protected open fun ScWriter.startNewInstance(info: ObjectPlaybackInfo) {
        throw NotImplementedError("startNewInstance not implemented for $this")
    }

    fun startNewInstance(info: ObjectPlaybackInfo): String {
        if (!affectsPlayback) return ""
        return writeCode {
            if (!isCreatedInSuperCollider) {
                createInSuperCollider(this)
                isCreatedInSuperCollider = true
            }
            startNewInstance(info)
        }
    }

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
        envelopesBeforeResize = envelopeControls()
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
                    if (spec != null && side in setOf(Side.LEFT, Side.RIGHT)) {
                        val env = envelopesBeforeResize[parameter] ?: continue
                        val offset = if (side == Side.LEFT) durationBeforeResize - targetDuration else zero
                        ctrl.points.copyFrom(env, offset, targetDuration)
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
        val instances = context.rootScore.instancesOf(this).toSet()
        for (inst in instances) {
            val score = inst.score
            if (score == null || score.isAuxiliary) continue
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
                ctrl.points.update()
            }
        }
        if (recordEdit) {
            val newEnvelopes = envelopeControls()
            context[UndoManager].record(
                ResizeEdit(
                    this, durationBeforeResize, heightBeforeResize,
                    this.duration, this.height,
                    envelopesBeforeResize, newEnvelopes,
                    mode, side
                )
            )
        }
    }

    private fun envelopeControls(): Map<String, Envelope> = if (this is SoundProcess) controls.mapNotNull { ctrl ->
        val value = ctrl.now
        if (value is EnvelopeControl) ctrl.name.now to value.points.copy()
        else null
    }.toMap() else emptyMap()

    fun resize(targetDuration: Decimal, targetHeight: Decimal, type: ResizeMode, side: Side) {
        beginResize(type, side)
        resize(targetDuration, targetHeight)
        finishResize()
    }

    protected abstract fun doClone(): ScoreObject

    protected open fun deepClone(): ScoreObject = doClone()

    fun clone(newName: String): ScoreObject {
        val obj = doClone()
        copyBasicPropertiesTo(obj)
        obj.setInitialName(newName)
        return obj
    }

    fun deepClone(newName: String): ScoreObject {
        val obj = deepClone()
        copyBasicPropertiesTo(obj)
        obj.setInitialName(newName)
        return obj
    }

    protected fun copyBasicPropertiesTo(obj: ScoreObject) {
        obj.duration = duration
        obj.height = height
        obj._associatedColor.now = associatedColor.now
        obj.memoText.now = memoText.now
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

    fun addedToScore(score: Score) {
        if (this is UnresolvedScoreObject) return
        check(initialized) { "$this was not initialized" }
        val registry = context[ScoreObjectRegistry]
        if (!registry.has(this)) {
            if (registry.has(this.name.now)) {
                _name!!.set(registry.nameForClone(this))
            }
            registry.context.withoutUndo { registry.add(this) }
        }
        if (!score.isAuxiliary) {
            instances.now += 1
        }
    }

    fun removedFromScore(score: Score, option: Score.RegistryOption) {
        if (this is UnresolvedScoreObject) return
        if (!score.isAuxiliary) {
            instances.now -= 1
        }
        if (option == Score.RegistryOption.KEEP_IN_REGISTRY) return
        if (!context.project.hasReferencesTo(this) && registry.has(this)) {
            val remove = !this.askBeforeDeleting || option == Score.RegistryOption.REMOVE_WITHOUT_ASKING || YesNoPrompt(
                "Score has no instances of $this anymore. Remove it from the registry?",
                cancellable = false,
                default = true
            ).showDialog(owner = context[primaryStage]) ?: false
            if (!remove) return
            context.withoutUndo { registry.remove(this) }
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
        private val oldEnvelopes: Map<String, Envelope>,
        private val newEnvelopes: Map<String, Envelope>,
        private val type: ResizeMode,
        private val side: Side
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Resize object"

        override fun doUndo() {
            obj.beginResize(type, side)
            obj.envelopesBeforeResize = emptyMap()
            obj.resize(oldDuration, oldHeight)
            updateEnvelopeControls(oldEnvelopes)
            obj.finishResize(recordEdit = false)
        }

        override fun doRedo() {
            obj.beginResize(type, side)
            obj.envelopesBeforeResize = emptyMap()
            obj.resize(newDuration, newHeight)
            updateEnvelopeControls(newEnvelopes)
            obj.finishResize(recordEdit = false)
        }

        private fun updateEnvelopeControls(map: Map<String, Envelope>) {
            if (obj is SoundProcess) {
                for ((parameter, envelope) in map) {
                    val ctrl = obj.controls.getControl(parameter)
                    if (ctrl !is EnvelopeControl) {
                        Logger.warn("Control for $parameter is not an envelope control", Logger.Category.Score)
                        continue
                    }
                    ctrl.points.copyFrom(envelope)
                }
            }
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
                    oldEnvelopes, other.newEnvelopes,
                    type, side,
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

        val ABSOLUTE_SCORE_Y = DataFormat("ponticello:absolute-score-y")
    }

}