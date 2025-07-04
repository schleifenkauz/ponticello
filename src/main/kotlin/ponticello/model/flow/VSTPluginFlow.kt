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
    private val pluginName: String,
    private val busRef: ReactiveVariable<BusReference>,
) : AudioFlow() {
    override lateinit var isValid: ReactiveValue<Boolean>
        private set

    private val client get() = context[SuperColliderClient]

    override fun copy(): VSTPluginFlow = VSTPluginFlow(pluginName, busRef.copy())

    @Transient
    lateinit var busSelector: BusSelector
        private set

    @Transient
    private lateinit var busesSelectionObserver: Observer

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
                client.run("if (s.serverRunning) { $superColliderName.set(\\bus, $bus) };")
            }
        }
        super.initialize(context)
    }

    override fun canRenameTo(newName: String): Boolean = true

    private val controllerName get() = "~plugin_${name.now}"

    override fun writeCode(placement: NodePlacement) = writeCode {
        val busName = busRef.now.get()?.superColliderName ?: "nil"
        +"$superColliderName = Synth(\\vst_plugin, [bus: $busName], addAction: ${placement.addAction}, target: ${placement.target})"
        +"s.sync"
        appendBlock("$superColliderName.onFree") {
            "$superColliderName = nil"
            "$controllerName = nil"
        }
        +"$controllerName = VSTPluginController($superColliderName)"
        +"$controllerName.open('$pluginName', editor: true)"
        +"s.sync"
        val stateFile = pluginStateFile().superColliderPath
        +"if (PathName(${stateFile}).isFile) { $controllerName.readProgram($stateFile) }"
        +"\"Opened plugin '$pluginName' in flow <${name.now}>\".postln"
        if (!isActive.now) {
            +"$superColliderName.set(\\bypass, 1)"
        }
    }

    override fun setRunning(active: Boolean) {
        val bypass = if (active) "0" else "1"
        context[SuperColliderClient].run("$superColliderName.set(\\bypass, $bypass)")
    }

    fun showEditor() {
        client.run("$controllerName.editor;")
    }

    fun saveConfiguration(): CompletableFuture<String> {
        val stateFile = pluginStateFile().invariantSeparatorsPath
        val controllerVar = controllerName.removePrefix("~")
        return client.send("save_plugin_state", listOf(controllerVar, stateFile))
    }

    private fun pluginStateFile(): File {
        val presetsDirectory = context[projectDirectory].resolve("plugin_states")
        presetsDirectory.mkdirs()
        val presetFile = presetsDirectory.resolve("${name.now}.fxp")
        return presetFile
    }

    override fun rename(newName: String) {
        val oldControllerName = controllerName
        super.rename(newName)
        val file = pluginStateFile()
        file.renameTo(file.resolveSibling("$newName.fxp"))
        client.run {
            +"$controllerName = $oldControllerName"
            +"$oldControllerName = nil"
        }
    }

    companion object {
        fun create(pluginName: String, bus: BusObject): VSTPluginFlow =
            VSTPluginFlow(pluginName, reactiveVariable(bus.reference()))

        fun availablePlugins(context: Context) = context[SuperColliderClient]
            .eval(
                "var str = \"\"; VSTPlugin.pluginList.do { |p| str = str ++ \", \" ++ p.name }; str;",
                description = "getting list of available plugins"
            ).get()
            .removePrefix(", ")
            .split(", ")
            .toSet()
    }
}