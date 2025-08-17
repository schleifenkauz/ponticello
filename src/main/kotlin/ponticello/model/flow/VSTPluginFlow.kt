package ponticello.model.flow

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.copy
import ponticello.impl.superColliderPath
import ponticello.impl.writeCode
import ponticello.model.obj.BusObject
import ponticello.model.obj.BusReference
import ponticello.model.project.PonticelloProject.Companion.projectDirectory
import ponticello.model.registry.ObjectList
import ponticello.model.registry.reference
import ponticello.sc.Rate
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import ponticello.sc.client.run
import ponticello.sc.editor.BusSelector
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.ReactiveVariable
import reaktive.value.binding.flatMap
import reaktive.value.now
import reaktive.value.reactiveVariable
import java.io.File
import java.util.concurrent.CompletableFuture

@Serializable
@SerialName("VSTPluginFlow")
class VSTPluginFlow private constructor(
    val pluginName: String,
    private val busRef: ReactiveVariable<BusReference>,
    val parameterMappings: VSTPluginParameterMappingList = VSTPluginParameterMappingList(),
) : AudioFlow(), ObjectList.Listener<VSTPluginParameterMapping> {
    override lateinit var isValid: ReactiveValue<Boolean>
        private set

    private val client get() = context[SuperColliderClient]

    override fun copy(): VSTPluginFlow = VSTPluginFlow(pluginName, busRef.copy(), parameterMappings.copy())

    @Transient
    lateinit var busSelector: BusSelector
        private set

    @Transient
    private lateinit var busesSelectionObserver: Observer

    val automatableParameters by lazy {
        val query = writeCode {
            +"var parameters = $controllerVar.info.parameters, str = \"\""
            +"parameters.do { |p| if (p.automatable) { str = str ++ \",\" ++ p.name } }"
            +"str"
        }
        context[SuperColliderClient].eval(
            query, description = "getting list of automatable parameters"
        ).get().removeSuffix(",").split(",").filter { it.isNotBlank() }
    }

    val supportsMidiInput by lazy {
        context[SuperColliderClient].eval("$controllerVar.info.midiInput").join().toBooleanStrictOrNull() ?: false
    }

    override fun initialize(context: Context) {
        if (initialized) return
        busSelector = BusSelector()
        busSelector.setFilter(Rate.Audio, 2)
        busSelector.syncWith(busRef)
        busSelector.initialize(context)

        isValid = busRef.flatMap(BusReference::isResolved)

        busesSelectionObserver = busRef.observe { _, _, newOutput ->
            val bus = newOutput.get()?.superColliderName
            if (bus != null) {
                client.run("$superColliderName.set(\\bus, $bus)")
            }
        }
        parameterMappings.initialize(context)
        parameterMappings.addListener(this, initialize = false)
        super.initialize(context)
    }

    override fun added(obj: VSTPluginParameterMapping, idx: Int) {
        context[SuperColliderClient].run {
            obj.applyTo(writer, this@VSTPluginFlow)
        }
    }

    override fun removed(obj: VSTPluginParameterMapping) {
        obj.dispose()
    }

    override fun canRenameTo(newName: String): Boolean = true

    val controllerVar get() = "~plugin_${name.now}"

    override fun writeCode(placement: NodePlacement) = writeCode {
        val busName = busRef.now.get()?.superColliderName ?: "nil"
        +"$superColliderName = Synth(\\vst_plugin, [bus: $busName], addAction: ${placement.addAction}, target: ${placement.target})"
        +"s.sync"
        appendBlock("$superColliderName.onFree") {
            +"$superColliderName = nil"
            +"$controllerVar = nil"
        }
        +"$controllerVar = VSTPluginController($superColliderName)"
        +"$controllerVar.open('$pluginName', editor: true, multiThreading: true)"
        +"s.sync"
        val stateFile = pluginStateFile().superColliderPath
        +"if (PathName(${stateFile}).isFile) { $controllerVar.readProgram($stateFile) }"
        if (!isActive.now) {
            +"$superColliderName.set(\\bypass, 1)"
        }
        +"s.sync"
        for (mapping in parameterMappings) {
            mapping.applyTo(writer, this@VSTPluginFlow)
        }
        +"\"Opened plugin '$pluginName' in flow <${name.now}>\".postln"
    }

    override fun setRunning(active: Boolean) {
        val bypass = if (active) "0" else "1"
        context[SuperColliderClient].run("$superColliderName.set(\\bypass, $bypass)")
    }

    fun showEditor() {
        client.run("$controllerVar.editor;")
    }

    fun saveConfiguration(): CompletableFuture<String> {
        val stateFile = pluginStateFile().invariantSeparatorsPath
        val controllerVar = controllerVar.removePrefix("~")
        return client.send("save_plugin_state", listOf(controllerVar, stateFile))
    }

    private fun pluginStateFile(): File {
        val presetsDirectory = context[projectDirectory].resolve("plugin_states")
        presetsDirectory.mkdirs()
        val presetFile = presetsDirectory.resolve("${name.now}.fxp")
        return presetFile
    }

    override fun rename(newName: String) {
        val oldControllerName = controllerVar
        super.rename(newName)
        val file = pluginStateFile()
        file.renameTo(file.resolveSibling("$newName.fxp"))
        client.run {
            +"$controllerVar = $oldControllerName"
            +"$oldControllerName = nil"
        }
    }

    override fun usesBus(bus: BusObject): Boolean = busRef.now.get() == bus.reference()

    companion object {
        fun create(pluginName: String, bus: BusObject): VSTPluginFlow =
            VSTPluginFlow(pluginName, reactiveVariable(bus.reference()), VSTPluginParameterMappingList())

        fun availablePlugins(context: Context) = context[SuperColliderClient]
            .eval(
                "var str = \"\"; VSTPlugin.pluginList.do { |p| str = str ++ \", \" ++ p.key }; str;",
                description = "getting list of available plugins"
            ).get()
            .removePrefix(", ")
            .split(", ")
            .toSet()
    }
}