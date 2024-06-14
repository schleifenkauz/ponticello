package xenakis.model

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.*
import xenakis.model.SuperColliderObject.LiveCycleType
import xenakis.model.XenakisProject.Companion.projectDirectory
import xenakis.sc.Rate
import xenakis.sc.editor.BusSelector

@Serializable
class VSTPluginObject private constructor(
    override val mutableName: ReactiveVariable<String>,
    private val pluginName: String,
    private val presetName: String,
    private var output: BusObjectReference,
    override val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color>
) : InstrumentObject, AbstractSuperColliderObject() {
    override val variableName get() = "~plugin_${name.now}"

    override val liveCycleType: LiveCycleType
        get() = LiveCycleType.ServerTree

    @Transient
    lateinit var outputSelector: BusSelector
        private set

    @Transient
    private lateinit var outputSelectorObserver: Observer

    @Transient
    private var controllerInfo: ControllerInfo? = null

    fun initialize(context: Context, controllerInfo: ControllerInfo) {
        this.controllerInfo = controllerInfo
        initialize(context)
    }

    override fun initialize(context: Context) {
        if (initialized) return
        outputSelector = BusSelector(context, Rate.Audio, 2, output)
        outputSelectorObserver = outputSelector.result.observe { _, _, newOutput ->
            output = newOutput
            client.run("if (s.serverRunning) { $variableName.synth.set(\\out, ${newOutput.get().variableName}) };")
        }
        super.initialize(context)
    }

    override fun ScWriter.allocateServerObject() {
        appendBlock("Task") {
            val info = controllerInfo
            if (info != null) {
                +"$variableName = VSTPluginController(${info.synthName}, \\${info.id})"
            } else {
                val synthName = "~tmp_synth"
                +"s.sync"
                +"2.wait"
                +"$synthName = Synth(\\vst_instrument, [out: ${output.get().variableName}])"
                +"s.sync"
                +"0.5.wait"
                +"$variableName = VSTPluginController($synthName)"
            }
            +"$variableName.open('$pluginName.vst3', editor: true, verbose: true)"
            +"s.sync"
            +"0.5.wait"
            val presetFile = context[projectDirectory].resolve("presets").resolve("$presetName.fxp").superColliderPath
            +"if (PathName(${presetFile}).isFile) { $variableName.readProgram($presetFile) }"
            +"'opened plugin'.postln"
        }
        appendLine(".play;")
    }

    override fun ScWriter.freeServerObject() {
        +"$variableName.close; $variableName.synth.free; $variableName = nil;"
    }

    override fun ScWriter.removeFromServer() {
        freeServerObject()
        "$variableName = nil;"
    }

    override fun canRenameTo(newName: String): Boolean =
        !(context[InstrumentRegistry].has(this) && context[InstrumentRegistry].has(newName))

    fun showEditor() {
        client.run("$variableName.editor;")
    }

    fun saveConfiguration() {
        val presetFile = context[projectDirectory].resolve("presets").resolve("$presetName.fxp")
        if (!presetFile.parentFile.isDirectory) presetFile.parentFile.mkdir()
        client.run("if (s.serverRunning) { $variableName.writeProgram(${presetFile.superColliderPath}) };")
    }

    data class ControllerInfo(val synthName: String, val id: String)

    companion object {
        fun create(context: Context, name: String, pluginName: String): VSTPluginObject {
            val presetName = "${System.currentTimeMillis()}"
            val color = randomColor()
            val output = context[BusRegistry].getDefault().createReference()
            return VSTPluginObject(reactiveVariable(name), pluginName, presetName, output, reactiveVariable(color))
        }

        fun availablePlugins(context: Context) = context[SuperColliderClient]
            .eval("var str = \"\"; VSTPlugin.pluginList.do { |p| str = str ++ \", \" ++ p.name }; str;")
            .join()
            .removePrefix(", ")
            .split(", ")

    }
}