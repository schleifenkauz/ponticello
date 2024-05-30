package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.core.editor.ListenerManager
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
) {
    @Transient
    private val views = ListenerManager.createWeakListenerManager<View>()

    @Transient
    lateinit var context: Context
        private set

    @Transient
    private lateinit var client: SuperColliderClient

    fun initialize(context: Context) {
        this.context = context
        context[SynthDefRegistry] = this
        client = context[SuperColliderClient]
    }

    var selectedSynthDef: SynthDefObject? = selectedSynthDefName?.let { name -> getSynthDefOrNull(name.now) }
        set(value) {
            if (value == field) return
            field = value
            selectedSynthDefName = value?.name
            views.notifyListeners { selectedSynthDef(value) }
        }

    init {
        selectedSynthDefName = selectedSynthDef?.name
    }

    fun getSynthDef(name: String): SynthDefObject =
        getSynthDefOrNull(name) ?: error("no SynthDef with name '$name'")

    private fun getSynthDefOrNull(name: String): SynthDefObject? = defs.find { it.name.now == name }

    fun hasSynthDef(name: String): Boolean = getSynthDefOrNull(name) != null

    fun synthDescLibContains(name: String): CompletableFuture<Boolean> {
        val answer = client.send("isSynthDef", listOf(name))
        return answer.thenApply { msg -> msg.boolean }
    }

    fun addSynthDef(obj: SynthDefObject, idx: Int = defs.size) {
        defs.add(idx, obj)
        obj.initialize(this)
        views.notifyListeners { addedSynthDef(idx, obj) }
    }

    fun removeSynthDef(obj: SynthDefObject) {
        val idx = defs.indexOf(obj)
        if (idx == -1) error("SynthDef ${obj.name} not found in registry")
        defs.removeAt(idx)
        obj.run { client.removeSynthDef() }
        views.notifyListeners { removedSynthDef(idx, obj) }
    }

    fun addView(view: View) {
        views.addListener(view)
        for ((idx, obj) in defs.withIndex()) {
            view.addedSynthDef(idx, obj)
        }
        view.selectedSynthDef(selectedSynthDef)
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

    interface View {
        fun addedSynthDef(idx: Int, obj: SynthDefObject)

        fun removedSynthDef(idx: Int, obj: SynthDefObject)

        fun selectedSynthDef(obj: SynthDefObject?)
    }
}