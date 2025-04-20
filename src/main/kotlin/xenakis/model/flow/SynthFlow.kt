package xenakis.model.flow

import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveString
import reaktive.value.ReactiveVariable
import reaktive.value.binding.flatMap
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.zero
import xenakis.model.obj.*
import xenakis.model.registry.reference
import xenakis.model.score.ObjectPosition
import xenakis.model.score.ParameterControlList
import xenakis.model.score.controls.BusControl
import xenakis.model.score.controls.writeSynthCode
import xenakis.sc.BusControlSpec
import xenakis.sc.client.ScWriter
import xenakis.sc.editor.SynthDefSelector

@Serializable
class SynthFlow(
    private var defRef: ReactiveVariable<SynthDefReference>,
    override val controls: ParameterControlList,
) : ParameterizedAudioFlow() {
    constructor(def: SynthDefObject, controls: ParameterControlList) : this(reactiveVariable(def.reference()), controls)

    @Transient
    lateinit var synthDefSelector: SynthDefSelector
        private set

    val synthDef get() = defRef.now.get() ?: NoSynthDef()

    override val def: ParameterizedObjectDef
        get() = synthDef

    override fun initialize(context: Context, bus: BusObject) {
        super.initialize(context, bus)
        synthDefSelector = SynthDefSelector()
        synthDefSelector.syncWith(defRef)
        synthDefSelector.initialize(context)
        controls.initialize(context, this)
    }

    override fun copy(): AudioFlow = SynthFlow(defRef, controls.copy())

    override fun ScWriter.writeCode(placement: NodePlacement) {
        val info = ScoreObjectInfo(ObjectPosition.ZERO, suffix = 0, placement, cutoff = zero)
        val mainBusParameter = getMainBusParameter(synthDef)!!
        val mainBusControl = mainBusParameter.name.now to Pair(
            mainBusParameter.spec.now,
            BusControl.create(associatedBus)
        )
        val name = superColliderName.now
        writeSynthCode(
            this@SynthFlow, name.removePrefix("~"), cutoff = zero, placement,
            latency = zero, extraControls = mapOf(mainBusControl), customSynthVar = name
        )
    }

    override fun addListener(listener: AudioNode.Listener) {
        controls.addListener(AudioNodeBusControlsListener(listener))
    }

    override fun getDefaultName(): ReactiveString = defRef.flatMap { ref -> ref.name }

    companion object {
        fun createFor(associatedBus: BusObject, def: SynthDefObject, context: Context): SynthFlow {
            val controls = def.defaultControls(context, defaultGroup = null, defaultBus = associatedBus.reference())
            val mainBusParam = getMainBusParameter(def)
            controls.removeIf { (name, _) -> name == "group" || name == mainBusParam!!.name.now }
            val flow = SynthFlow(reactiveVariable(def.reference()), ParameterControlList.from(controls))
            return flow
        }

        fun getMainBusParameter(def: SynthDefObject) =
            def.parameters.firstOrNull { p -> p.spec.now is BusControlSpec }
    }
}