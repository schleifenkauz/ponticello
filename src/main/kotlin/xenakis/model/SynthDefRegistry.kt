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
class SynthDefRegistry private constructor(
    private val defs: MutableList<SynthDefObject>,
    private var selectedSynthDefName: ReactiveValue<String>? = null
) : ObjectRegistry<SynthDefObject>() {
    override val objects: MutableList<SynthDefObject>
        get() = defs

    override val objectType: String
        get() = "SynthDef"

    @Transient
    private lateinit var client: SuperColliderClient

    override fun initialize(context: Context) {
        super.initialize(context)
        context[SynthDefRegistry] = this
        client = context[SuperColliderClient]
    }

    var selectedSynthDef: SynthDefObject? = selectedSynthDefName?.let { name -> getSynthDefOrNull(name.now) }
        set(value) {
            if (value == field) return
            field = value
            selectedSynthDefName = value?.name
            views.notifyListeners { if (this is View) selected(value) }
        }

    init {
        selectedSynthDefName = selectedSynthDef?.name
    }

    private fun getSynthDefOrNull(name: String): SynthDefObject? = defs.find { it.name.now == name }

    override fun getDefault(): SynthDefObject = StandardSynthDefObject.default

    fun synthDescLibContains(name: String): CompletableFuture<Boolean> {
        val answer = client.send("isSynthDef", listOf(name))
        return answer.thenApply { msg -> msg.boolean }
    }

    override fun onAdded(obj: SynthDefObject, idx: Int) {
        obj.run { client.sync() }
    }

    override fun onRemoved(obj: SynthDefObject, idx: Int) {
        obj.run { client.removeSynthDef() }
    }

    fun addView(view: View) {
        super.addView(view)
        view.selected(selectedSynthDef)
    }

    fun sync() {
        for (def in defs) {
            def.run { client.sync() }
        }
    }

    fun SuperColliderContext.addSynthDefs() {
        for (def in defs) {
            if (def is CustomizableSynthDefObject) {
                def.run { addToGlobalSynthDescLib() }
            }
        }
    }

    companion object : PublicProperty<SynthDefRegistry> by publicProperty("SynthDefs") {
        fun newInstance(): SynthDefRegistry = SynthDefRegistry(StandardSynthDefObject.all.values.toMutableList())
    }

    interface View : ObjectRegistry.View<SynthDefObject> {
        fun selected(obj: SynthDefObject?)
    }
}