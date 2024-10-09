package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.SuperColliderClient

@Serializable
class InstrumentRegistry private constructor(
    private val instruments: MutableList<InstrumentObject>,
    @SerialName("selectedInstrument") private val selectedInstrumentRef: ReactiveVariable<ObjectReference?>
) : SuperColliderObjectRegistry<InstrumentObject>() {
    override val objects: MutableList<InstrumentObject>
        get() = instruments

    override val objectType: String
        get() = "Instrument"

    @Transient
    private lateinit var client: SuperColliderClient

    @Transient
    lateinit var selectedInstrument: ReactiveValue<InstrumentObject?>
        private set

    override fun initialize(context: Context) {
        super.initialize(context)
        context[InstrumentRegistry] = this
        client = context[SuperColliderClient]
        selectedInstrumentRef.now?.resolve(this)
        selectedInstrument = selectedInstrumentRef.map { ref -> ref?.get() }
    }

    override fun getDefault(): InstrumentObject = selectedInstrument.now ?: error("No default SynthDefObject available")

    fun select(instrument: InstrumentObject?) {
        selectedInstrumentRef.now = instrument?.createReference()
        views.notifyListeners { if (this is Listener) this.selected(instrument) }
    }

    fun synthDescLibContains(name: String): Boolean {
        val answer = client.send("isSynthDef", listOf(name))
        return answer.get().toBoolean()
    }

    fun addView(view: Listener) {
        super.addListener(view)
        view.selected(selectedInstrument.now)
    }

    companion object : PublicProperty<InstrumentRegistry> by publicProperty("InstrumentRegistry") {
        fun createDefault(): InstrumentRegistry =
            InstrumentRegistry(StandardSynthDefObject.all.values.toMutableList(), reactiveVariable(null))
    }

    interface Listener : ObjectRegistry.Listener<InstrumentObject> {
        fun selected(obj: InstrumentObject?)
    }
}