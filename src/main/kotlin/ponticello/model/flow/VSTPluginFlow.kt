package ponticello.model.flow

import fxutils.undo.AbstractEdit
import fxutils.undo.UndoManager
import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.Logger
import ponticello.impl.copy
import ponticello.impl.superColliderPath
import ponticello.impl.writeCode
import ponticello.model.instr.BusObject
import ponticello.model.obj.BusReference
import ponticello.model.project.PonticelloProject.Companion.projectDirectory
import ponticello.model.registry.ObjectList
import ponticello.model.registry.reference
import ponticello.sc.Rate
import ponticello.sc.client.*
import ponticello.sc.editor.BusSelector
import reaktive.Observer
import reaktive.value.*
import reaktive.value.binding.flatMap
import java.io.File
import java.util.concurrent.CompletableFuture

@Serializable
@SerialName("VSTPluginFlow")
class VSTPluginFlow private constructor(
    @SerialName("pluginName") private val _pluginName: ReactiveVariable<String>,
    private val busRef: ReactiveVariable<BusReference>,
    val parameterMappings: VSTPluginParameterMappingList = VSTPluginParameterMappingList(),
) : AudioFlow(), ObjectList.Listener<VSTPluginParameterMapping> {
    @SerialName("name")
    override var _name: ReactiveVariable<String>? = null

    override lateinit var isValid: ReactiveValue<Boolean>
        private set

    private var preset: String? = null

    val pluginName: ReactiveString get() = _pluginName

    override fun copy(): VSTPluginFlow =
        VSTPluginFlow(_pluginName.copy(), busRef.copy(), parameterMappings.copy())

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
        try {
            context[SuperColliderClient].eval("$controllerVar.info.midiInput").join().toBooleanStrictOrNull() ?: false
        } catch (e: SuperColliderException) {
            Logger.error("Error getting VST info", e)
            false
        }
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

    fun loadPlugin(plugin: String) {
        val oldPluginName = pluginName.now
        _pluginName.now = plugin
        client.run("$controllerVar.open('${pluginName.now}', editor: true, multiThreading: true)")
        context[UndoManager].record(LoadPlugin(this, plugin, oldPluginName))
    }

    fun loadGlobalPreset(presetName: String) {
        saveConfiguration().join()
        client.run {
            doLoadPreset(presetName)
        }
        context[UndoManager].record(LoadGlobalPreset(this, presetName))
    }

    private fun ScWriter.doLoadPreset(presetName: String) {
        +"$controllerVar.loadPreset('$presetName')"
    }

    fun saveGlobalPreset(presetName: String) {
        client.run("$controllerVar.savePreset('$presetName')")
    }

    override fun added(obj: VSTPluginParameterMapping, idx: Int) {
        context[SuperColliderClient].run {
            obj.applyTo(writer, this@VSTPluginFlow)
        }
    }

    override fun removed(obj: VSTPluginParameterMapping, idx: Int) {
        obj.dispose()
    }

    override fun canRenameTo(newName: String): Boolean = true

    val controllerVar get() = "~plugin_${name.now}"

    override fun ScWriter.createObject() {
    }

    override fun writeCode(placement: NodePlacement) = writeCode {
        val busName = busRef.now.get()?.superColliderName ?: "nil"
        val pluginName = pluginName.now
        val drySignal = if (VSTPlugins.hasInputs(pluginName, context)) 1 else 0
        +"$superColliderName = Synth(\\vst_plugin, [bus: $busName, dry: $drySignal], ${placement.code})"
        +"s.sync"
        appendBlock("$superColliderName.onFree") {
            +"$superColliderName = nil"
            +"$controllerVar = nil"
        }
        +"$controllerVar = VSTPluginController($superColliderName)"
        +"$controllerVar.open('$pluginName', editor: true, multiThreading: true)"
        +"s.sync"
        if (preset != null) {
            doLoadPreset(preset!!)
        } else {
            val stateFile = pluginStateFile().superColliderPath
            +"if (PathName(${stateFile}).isFile) { $controllerVar.readProgram($stateFile) }"
        }
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
        preset = null
        val stateFile = pluginStateFile().invariantSeparatorsPath
        val controllerVar = controllerVar.removePrefix("~")
        return client.send("save_plugin_state", listOf(controllerVar, stateFile))
    }

    fun loadConfiguration() {
        val stateFile = pluginStateFile().superColliderPath
        client.run("if (PathName(${stateFile}).isFile) { $controllerVar.readProgram($stateFile) }")
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

    private class LoadPlugin(
        private val flow: VSTPluginFlow,
        private val newPluginName: String,
        private val oldPluginName: String
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Load VST Plugin"

        override fun doRedo() {
            flow.loadPlugin(newPluginName)
        }

        override fun doUndo() {
            flow.loadPlugin(oldPluginName)
        }
    }

    private class LoadGlobalPreset(
        private val flow: VSTPluginFlow,
        private val preset: String,
    ) : AbstractEdit() {
        override val actionDescription: String
            get() = "Load global preset"

        override fun doRedo() {
            flow.loadGlobalPreset(preset)
        }

        override fun doUndo() {
            flow.loadConfiguration()
        }
    }

    companion object {
        fun create(pluginName: String, preset: String?, bus: BusObject): VSTPluginFlow = VSTPluginFlow(
            reactiveVariable(pluginName), reactiveVariable(bus.reference()), VSTPluginParameterMappingList()
        ).also { it.preset = preset }
    }
}