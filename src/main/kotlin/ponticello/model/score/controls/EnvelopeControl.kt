package ponticello.model.score.controls

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.ColorSerializer
import ponticello.impl.Logger
import ponticello.model.instr.ParameterizedObject
import ponticello.model.score.Envelope
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl
import ponticello.sc.ControlSpec
import ponticello.sc.NumericalControlSpec
import ponticello.sc.Warp
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.score.EnvelopeView
import reaktive.Observer
import reaktive.event.unitEvent
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.util.concurrent.CompletableFuture

@Serializable
@SerialName("Envelope")
class EnvelopeControl(
    val points: Envelope,
    val displayColor: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color> = reactiveVariable(Color.BLACK),
    val display: ReactiveVariable<Boolean> = reactiveVariable(true),
) : ParameterControl(), EnvelopeView {
    @Transient
    private var index = -1

    @Transient
    private lateinit var specObserver: Observer

    @Transient
    val update = unitEvent()

    @Transient
    lateinit var synthDefSynchronizerJob: CompletableFuture<String>
        private set

    @Transient
    private lateinit var associatedControl: NamedParameterControl

    override fun initialize(context: Context, namedControl: NamedParameterControl) {
        index = counter++
        super.initialize(context, namedControl)
        points.initialize(context)
        specObserver = namedControl.spec.observe { _, old, new ->
            when {
                old !is NumericalControlSpec && new is NumericalControlSpec -> updateSynthDef()
                old is NumericalControlSpec && new is NumericalControlSpec && old.warp != new.warp -> updateSynthDef()
            }
        }
        synthDefSynchronizerJob = CompletableFuture.completedFuture("")
        associatedControl = namedControl
        updateSynthDef()
        synthDefSynchronizerJob.join()
        points.addListener(this)
    }

    private fun updateSynthDef() {
        if (points.size < 2) {
            Logger.warn("Envelope with less than 2 points", Logger.Category.Score)
            return
        }
        val spec = associatedControl.spec.now
        if (spec !is NumericalControlSpec) {
            System.err.println("Expected NumericalControlSpec but got $spec")
        }
        val warp = (spec as? NumericalControlSpec)?.warp
        synthDefSynchronizerJob.join()
        val curve = (warp ?: Warp.Linear).toString()
        val arguments = mutableListOf<Any>(index, points.size, curve)
        arguments.addAll(points.getLevels())
        arguments.addAll(points.getTimes())
        val job = context[SuperColliderClient].send("addEnvDef", arguments)
        synthDefSynchronizerJob = job
    }

    override fun copy(): ParameterControl =
        EnvelopeControl(points = points.copy(), displayColor, display)

    override fun validate(spec: ControlSpec, obj: ParameterizedObject): Boolean = spec is NumericalControlSpec

    override fun writeCode(spec: ControlSpec?, obj: ParameterizedObject): String {
        val warp = if (spec is NumericalControlSpec) spec.warp else Warp.Linear
        return "EnvelopeControl(${points.code(warp)})"
    }

    override fun editedEnvelope() {
        updateSynthDef()
        update.fire()
    }

    companion object {
        private var counter = 0

        fun resetCounter() {
            counter = 0
        }
    }
}