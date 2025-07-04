package ponticello.model.score

import hextant.context.Context
import javafx.geometry.HorizontalDirection
import javafx.geometry.HorizontalDirection.LEFT
import javafx.geometry.HorizontalDirection.RIGHT
import javafx.geometry.Side
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Decimal
import ponticello.impl.copy
import ponticello.impl.zero
import ponticello.model.flow.NodePlacement
import ponticello.model.obj.BusReference
import ponticello.model.obj.ParameterDefObject
import ponticello.model.obj.withName
import ponticello.model.registry.ObjectReference
import ponticello.model.score.controls.ParameterControl
import ponticello.sc.editor.BusSelector
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
@SerialName("SubScore")
class ScoreObjectGroup(
    val score: Score,
    @SerialName("defaultBus") val defaultBusRef: ReactiveVariable<BusReference> = reactiveVariable(ObjectReference.none())
) : ScoreObject() {
    override val type: String
        get() = "compound"

    override val affectsPlayback: Boolean
        get() = score.objectInstances.any { inst -> inst.obj.affectsPlayback }

    @Transient
    lateinit var busSelector: BusSelector
        private set

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        busSelector = BusSelector()
        busSelector.syncWith(defaultBusRef)
        busSelector.initialize(context)
        this.score.initialize(context, this)
    }

    override fun writeCode(
        uniqueName: String,
        placement: NodePlacement?,
        cutoff: Decimal,
        latency: Decimal,
        extraArguments: Map<ParameterDefObject, ParameterControl>
    ): String = ""

    override fun doCut(position: Decimal, whichHalf: HorizontalDirection): ScoreObject {
        val objects = mutableListOf<ScoreObjectInstance>()
        for (inst in score.objectInstances) {
            when {
                whichHalf == LEFT && inst.start + inst.duration <= position -> {
                    objects.add(inst)
                }

                whichHalf == LEFT && inst.start < position -> {
                    val leftHalf = inst.cut(position - inst.start, LEFT) ?: inst
                    objects.add(leftHalf)
                }

                whichHalf == RIGHT && inst.start >= position -> {
                    inst.setTime(inst.start - position)
                    objects.add(inst)
                }

                whichHalf == RIGHT && inst.start + inst.duration > position -> {
                    val rightHalf = inst.cut(position - inst.start, RIGHT) ?: continue
                    objects.add(rightHalf)
                }
            }
        }
        val score = Score(objects)
        return ScoreObjectGroup(score, defaultBusRef.copy())
    }

    fun cutVertically(position: Decimal): Pair<ScoreObjectGroup, ScoreObjectGroup> {
        val top = mutableListOf<ScoreObjectInstance>()
        val bottom = mutableListOf<ScoreObjectInstance>()
        for (inst in score.objectInstances) {
            if (inst.y < position) top.add(inst)
            else bottom.add(ScoreObjectInstance(inst.obj, inst.start, inst.y - position))
        }
        val obj1 = ScoreObjectGroup(Score(top), defaultBusRef.copy()).withName(name.now + "_top")
        val obj2 = ScoreObjectGroup(Score(bottom), defaultBusRef.copy()).withName(name.now + "_bot")
        obj1.setInitialSize(duration, position)
        obj2.setInitialSize(duration, height - position)
        return Pair(obj1, obj2)
    }

    override fun beginResize(mode: ResizeMode, side: Side): Boolean {
        super.beginResize(mode, side)
        if (mode.isStretch || side == Side.LEFT || side == Side.TOP) {
            for (inst in score.objectInstances) {
                inst.beginMove()
            }
        }
        if (mode == ResizeMode.DeepStretch) {
            for (obj in score.objects) {
                obj.beginResize(ResizeMode.DeepStretch, side)
            }
        }
        return true
    }

    override fun resize(targetDuration: Decimal, targetHeight: Decimal) {
        var minDur = zero(ObjectPosition.TIME_PRECISION)
        var minHeight = zero(ObjectPosition.Y_PRECISION)
        val objects = score.objectInstances
        val mode = resizeMode ?: error("resizeMode is not set")
        if (objects.isNotEmpty() && !mode.isStretch) { //todo compute min dimensions when resizeType=Stretch
            minDur =
                if (resizeSide == Side.LEFT) this.duration - objects.minOf { o -> o.start }
                else objects.maxOf { o -> o.start + o.duration }

            minHeight =
                if (resizeSide == Side.TOP) this.height - objects.minOf { o -> o.y }
                else objects.maxOf { o -> o.y + o.height }
        }
        val deltaDur = targetDuration.coerceAtLeast(minDur) - this.duration
        val deltaHeight = targetHeight.coerceAtLeast(minHeight) - this.height
        super.resize(this.duration + deltaDur, this.height + deltaHeight)
        for (inst in score.objectInstances) {
            if (mode.isStretch) {
                val factorT = this.duration / durationBeforeResize
                val factorY = this.height / heightBeforeResize
                val newTime = inst.positionBeforeMove.time * factorT
                val newY = inst.positionBeforeMove.y * factorY
                inst.moveTo(newTime, newY, simpleMove = false)
                if (mode == ResizeMode.DeepStretch) {
                    val obj = inst.obj
                    obj.resize(obj.durationBeforeResize * factorT, obj.heightBeforeResize * factorY)
                }
            } else {
                val newTime = if (resizeSide == Side.LEFT) inst.start + deltaDur else inst.start
                val newY = if (resizeSide == Side.TOP) inst.y + deltaHeight else inst.y
                inst.moveTo(newTime, newY, simpleMove = false)
            }
        }
    }

    override fun finishResize(recordEdit: Boolean) {
        super.finishResize(recordEdit)
        val mode = resizeMode ?: error("resizeMode is not set")
        if (mode == ResizeMode.DeepStretch) {
            for (obj in score.objects) {
                obj.finishResize(recordEdit = false)
            }
        }
        if (mode.isStretch || resizeSide == Side.LEFT || resizeSide == Side.TOP) {
            for (inst in score.objectInstances) {
                inst.finishMove(notifyScore = false, recordEdit = false)
            }
        }
    }

    override fun doClone(): ScoreObject =
        ScoreObjectGroup(score.clone(), defaultBusRef.copy())

    override fun onRemoved() {
        super.onRemoved()
        for (obj in score.objects) {
            obj.onRemoved()
        }
    }
}