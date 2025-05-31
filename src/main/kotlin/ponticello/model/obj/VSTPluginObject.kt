package ponticello.model.obj

import hextant.context.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import ponticello.impl.randomColor
import ponticello.impl.superColliderPath
import ponticello.model.project.PonticelloProject.Companion.projectDirectory
import ponticello.model.registry.BusRegistry
import ponticello.model.registry.ObjectReference
import ponticello.model.registry.reference
import ponticello.sc.Rate
import ponticello.sc.client.ScWriter
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import ponticello.sc.editor.BusSelector
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable

@Serializable
class VSTPluginObject private constructor(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    private val pluginName: String,
    private val presetName: String,
    private var output: ObjectReference<BusObject>,
) : AbstractSuperColliderObject() {
    override val superColliderName get() = "~plugin_${name.now}"

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
        outputSelector = BusSelector()
        outputSelector.setFilter(Rate.Audio, 2)
        outputSelector.selectInitial(output)
        outputSelectorObserver = outputSelector.result.observe { _, _, newOutput ->
            output = newOutput
            val bus = output.get()?.superColliderName
            if (bus != null) client.run("if (s.serverRunning) { $superColliderName.synth.set(\\out, $bus) };")
        }
        super.initialize(context)
    }

    override fun canRenameTo(newName: String): Boolean = true

    override fun ScWriter.createObject() {
        appendBlock("Task", endLine = false) {
            val info = controllerInfo
            if (info != null) {
                +"$superColliderName = VSTPluginController(${info.synthName}, \\${info.id})"
            } else {
                val synthName = "~tmp_synth"
                +"s.sync"
                +"2.wait"
                +"$synthName = Synth(\\vst_instrument, [out: ${output.superColliderName}])"
                +"s.sync"
                +"0.5.wait"
                +"$superColliderName = VSTPluginController($synthName)"
            }
            +"$superColliderName.open('$pluginName.vst3', editor: true, verbose: true)"
            +"s.sync"
            +"0.5.wait"
            val presetFile = context[projectDirectory].resolve("presets").resolve("$presetName.fxp").superColliderPath
            +"if (PathName(${presetFile}).isFile) { $superColliderName.readProgram($presetFile) }"
            +"'opened plugin'.postln"
        }
        appendLine(".play;")
    }

    override fun ScWriter.freeObject() {
        +"$superColliderName.close; $superColliderName.synth.free; $superColliderName = nil"
    }

    fun showEditor() {
        client.run("$superColliderName.editor;")
    }

    fun saveConfiguration() {
        val presetFile = context[projectDirectory].resolve("presets").resolve("$presetName.fxp")
        if (!presetFile.parentFile.isDirectory) presetFile.parentFile.mkdir()
        client.run("if (s.serverRunning) { $superColliderName.writeProgram(${presetFile.superColliderPath}) };")
    }

    data class ControllerInfo(val synthName: String, val id: String)

    companion object {
        fun create(context: Context, name: String, pluginName: String): VSTPluginObject {
            val presetName = "${System.currentTimeMillis()}"
            randomColor()
            val output = context[BusRegistry].getDefault().reference()
            return VSTPluginObject(reactiveVariable(name), pluginName, presetName, output)
        }

        fun availablePlugins(context: Context) = context[SuperColliderClient]
            .eval("var str = \"\"; VSTPlugin.pluginList.do { |p| str = str ++ \", \" ++ p.name }; str;").get()
            .removePrefix(", ")
            .split(", ")
            .toSet()
    }
}