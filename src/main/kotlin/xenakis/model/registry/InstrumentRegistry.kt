package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.binding.map
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.obj.InstrumentObject
import xenakis.model.obj.ReferencedSynthDefObject
import xenakis.model.obj.SuperColliderObject
import xenakis.sc.client.SuperColliderClient

@Serializable
class InstrumentRegistry(
    private val instruments: MutableList<InstrumentObject>,
    @SerialName("selectedInstrument") private val selectedInstrumentRef: ReactiveVariable<ObjectReference?>
) : SuperColliderObjectRegistry<InstrumentObject>() {
    override val objects: MutableList<InstrumentObject>
        get() = instruments

    override val liveCycleType: SuperColliderObject.LiveCycleType
        get() = SuperColliderObject.LiveCycleType.InterpreterBoot //TODO this doesn't work for VSTPlugins

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

    override fun getDefault(name: String?): InstrumentObject =
        selectedInstrument.now ?: error("No default SynthDefObject available")

    fun select(instrument: InstrumentObject?) {
        selectedInstrumentRef.now = instrument?.reference()
        views.notifyListeners { if (this is Listener) this.selected(instrument) }
    }

    fun synthDescLibContains(name: String): Boolean {
        val answer = client.send("isSynthDef", listOf(name))
        return answer.get().toBoolean()
    }

    fun addView(view: Listener) {
        super.addListener(view, initialize = true)
        view.selected(selectedInstrument.now)
    }

    companion object : PublicProperty<InstrumentRegistry> by publicProperty("InstrumentRegistry") {
        fun createDefault(): InstrumentRegistry =
            InstrumentRegistry(mutableListOf(), reactiveVariable(null))

        fun defaultInstrument() = ReferencedSynthDefObject("default", reactiveVariable(Color.WHEAT))
    }

    interface Listener : ObjectRegistry.Listener<InstrumentObject> {
        fun selected(obj: InstrumentObject?)
    }
}