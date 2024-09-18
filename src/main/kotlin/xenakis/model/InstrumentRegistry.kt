package xenakis.model

import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveValue
import reaktive.value.now
import xenakis.impl.SuperColliderClient
import xenakis.impl.boolean
import java.util.concurrent.CompletableFuture

@Serializable
class InstrumentRegistry private constructor(
    private val instruments: MutableList<InstrumentObject>,
    private var selectedInstrumentName: ReactiveValue<String>? = null
) : SuperColliderObjectRegistry<InstrumentObject>() {
    override val objects: MutableList<InstrumentObject>
        get() = instruments

    override val objectType: String
        get() = "Instrument"

    @Transient
    private lateinit var client: SuperColliderClient

    override fun initialize(context: Context) {
        super.initialize(context)
        context[local] = this
        client = context[SuperColliderClient]
    }

    var selectedInstrument: InstrumentObject? = selectedInstrumentName?.let { name -> getSynthDefOrNull(name.now) }
        set(value) {
            if (value == field) return
            field = value
            selectedInstrumentName = value?.name
            views.notifyListeners { if (this is Listener) selected(value) }
        }

    init {
        selectedInstrumentName = selectedInstrument?.name
    }

    private fun getSynthDefOrNull(name: String): InstrumentObject? = instruments.find { it.name.now == name }

    override fun getDefault(): SynthDefObject = StandardSynthDefObject.default

    fun synthDescLibContains(name: String): CompletableFuture<Boolean> {
        val answer = client.send("isSynthDef", listOf(name))
        return answer.thenApply { msg -> msg.boolean }
    }

    fun addView(view: Listener) {
        super.addListener(view)
        view.selected(selectedInstrument)
    }

    companion object {
        val local = publicProperty<InstrumentRegistry>("local InstrumentRegistry")
        val global = publicProperty<InstrumentRegistry>("global InstrumentRegistry")

        fun createDefault(): InstrumentRegistry = InstrumentRegistry(StandardSynthDefObject.all.values.toMutableList())
    }

    interface Listener : ObjectRegistry.Listener<InstrumentObject> {
        fun selected(obj: InstrumentObject?)
    }
}