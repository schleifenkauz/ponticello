package xenakis.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.zero
import xenakis.model.obj.*
import xenakis.model.player.ParameterControlLiveUpdater
import xenakis.model.registry.reference
import xenakis.model.score.BusControl
import xenakis.model.score.ConstantControl
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ParameterControls
import xenakis.sc.BusControlSpec
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.editor.SynthDefSelector
import xenakis.sc.writeSynthCode

@Serializable
class SynthFlow(
    private var defRef: ReactiveVariable<SynthDefReference>,
    override val controls: ParameterControls,
) : AudioFlow(), ParameterizedObject {
    @Transient
    lateinit var synthDefSelector: SynthDefSelector
        private set

    val synthDef get() = defRef.now.get() ?: NoSynthDef()

    @Transient
    private lateinit var listener: ParameterControlLiveUpdater

    override val def: ParameterizedObjectDef
        get() = synthDef

    override fun initialize(context: Context, bus: BusObject) {
        super.initialize(context, bus)
        synthDefSelector = SynthDefSelector()
        synthDefSelector.syncWith(defRef)
        synthDefSelector.initialize(context)
        controls.initialize(context, this)
        listener = ParameterControlLiveUpdater(context[SuperColliderClient]) { listOf(superColliderName.now) }
        listener.listen(controls)
    }

    override fun copy(): AudioFlow = SynthFlow(defRef, controls.copy())

    override fun ScWriter.writeCode(placement: NodePlacement) {
        val synthVar = superColliderName.now
        val info = ScoreObjectInfo(ObjectPosition.ZERO, synthVar.removePrefix("~"), synthVar, placement)
        val mainBusControl = getMainBusParameter(synthDef)!! to BusControl.create(associatedBus)
        val withoutDuration = "afterDuration" to ConstantControl.create(zero) //no free after duration
        writeSynthCode(
            synthDef, context,
            info, duration = null,
            controls.controlMap + mainBusControl + withoutDuration
        )
    }

    override fun getInputs(): Collection<BusObject> = super.getInputs()

    override fun getOutputs(): Collection<BusObject> = super.getOutputs()

    override fun addListener(listener: AudioNode.Listener) {
        controls.addListener(AudioNodeBusControlsListener(listener))
    }

    override fun getDefaultName(): String = "Synth"

    companion object {
        fun createFor(associatedBus: BusObject, def: SynthDefObject, context: Context): SynthFlow {
            val controls = def.defaultControls(context, defaultGroup = null, defaultBus = associatedBus.reference())
            val mainBusParam = getMainBusParameter(def)
            controls.removeIf { (name, _) -> name == "group" || name == mainBusParam }
            val flow = SynthFlow(reactiveVariable(def.reference()), ParameterControls.from(controls))
            return flow
        }

        fun getMainBusParameter(def: SynthDefObject) =
            def.parameters.firstOrNull { p -> p.spec.now is BusControlSpec }?.name?.now
    }
}