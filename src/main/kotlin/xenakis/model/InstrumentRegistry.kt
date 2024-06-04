package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.ReactiveValue
import reaktive.value.now
import xenakis.impl.SuperColliderClient
import xenakis.impl.SuperColliderContext
import xenakis.impl.boolean
import java.util.concurrent.CompletableFuture

@Serializable
class InstrumentRegistry private constructor(
    private val instruments: MutableList<InstrumentObject>,
    private var selectedInstrumentName: ReactiveValue<String>? = null
) : ObjectRegistry<InstrumentObject>() {
    override val objects: MutableList<InstrumentObject>
        get() = instruments

    override val objectType: String
        get() = "Instrument"

    @Transient
    private lateinit var client: SuperColliderClient

    override fun initialize(context: Context) {
        super.initialize(context)
        context[InstrumentRegistry] = this
        client = context[SuperColliderClient]
    }

    var selectedInstrument: InstrumentObject? = selectedInstrumentName?.let { name -> getSynthDefOrNull(name.now) }
        set(value) {
            if (value == field) return
            field = value
            selectedInstrumentName = value?.name
            views.notifyListeners { if (this is View) selected(value) }
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

    override fun onAdded(obj: InstrumentObject, idx: Int) {
        if (obj is VSTPluginObject) {
            obj.initializeNew(context)
        } else {
            obj.initialize(context)
            obj.run { client.sync() }
        }
    }

    override fun onRemoved(obj: InstrumentObject, idx: Int) {
        obj.run { client.remove() }
    }

    fun addView(view: View) {
        super.addView(view)
        view.selected(selectedInstrument)
    }

    fun sync() {
        for (def in instruments) {
            def.run { client.sync() }
        }
    }

    fun SuperColliderContext.addSynthDefs() {
        for (def in instruments) {
            if (def is CustomizableSynthDefObject) {
                def.run { addToGlobalSynthDescLib() }
            }
        }
    }

    fun SuperColliderContext.loadVSTPlugins() {
        for (instr in instruments) {
            if (instr is VSTPluginObject) {
                //val channelsCode = "VSTPlugin.plugins['$pluginName'].outputs[0].channels"
                //val outputChannels = client.eval(channelsCode).join().toIntOrNull() ?: 2
                instr.run { loadVSTPlugin() }
            }
        }
    }

    companion object : PublicProperty<InstrumentRegistry> by publicProperty("SynthDefs") {
        fun newInstance(): InstrumentRegistry = InstrumentRegistry(StandardSynthDefObject.all.values.toMutableList())
    }

    interface View : ObjectRegistry.View<InstrumentObject> {
        fun selected(obj: InstrumentObject?)
    }
}