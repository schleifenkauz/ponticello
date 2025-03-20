package xenakis.model.obj

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ColorSerializer
import xenakis.impl.randomColor
import xenakis.impl.superColliderPath
import xenakis.model.project.XenakisProject.Companion.projectDirectory
import xenakis.model.registry.BusRegistry
import xenakis.model.registry.InstrumentRegistry
import xenakis.model.registry.ObjectReference
import xenakis.model.registry.reference
import xenakis.sc.Rate
import xenakis.sc.client.ScWriter
import xenakis.sc.client.SuperColliderClient
import xenakis.sc.editor.BusSelector

@Serializable
class VSTPluginObject private constructor(
    @SerialName("name") override val mutableName: ReactiveVariable<String>,
    private val pluginName: String,
    private val presetName: String,
    private var output: ObjectReference<BusObject>,
    override val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color>
) : InstrumentObject, AbstractSuperColliderObject() {
    override val superColliderName get() = "~plugin_${name.now}"

    @Transient
    lateinit var outputSelector: BusSelector
        private set

    @Transient
    private lateinit var outputSelectorObserver: Observer

    @Transient
    private var controllerInfo: ControllerInfo? = null

    override fun copy(name: String): InstrumentObject =
        throw UnsupportedOperationException("Cannot copy $this")

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

    override fun canRenameTo(newName: String): Boolean =
        !(context[InstrumentRegistry].has(this) && context[InstrumentRegistry].has(newName))

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
            val color = randomColor()
            val output = context[BusRegistry].getDefault().reference()
            return VSTPluginObject(reactiveVariable(name), pluginName, presetName, output, reactiveVariable(color))
        }

        fun availablePlugins(context: Context) = context[SuperColliderClient]
            .eval("var str = \"\"; VSTPlugin.pluginList.do { |p| str = str ++ \", \" ++ p.name }; str;").get()
            .removePrefix(", ")
            .split(", ")
            .toSet()
    }
}