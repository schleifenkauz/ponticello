package xenakis.model

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.Observer
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.ColorSerializer
import xenakis.impl.SuperColliderClient
import xenakis.impl.SuperColliderContext
import xenakis.impl.randomColor
import xenakis.sc.Rate
import xenakis.sc.editor.AbstractRenamableObject
import xenakis.sc.editor.BusSelector

@Serializable
class VSTPluginObject private constructor(
    override val mutableName: ReactiveVariable<String>,
    private val pluginName: String,
    private val presetName: String,
    private var output: BusObjectReference,
    override val color: ReactiveVariable<@Serializable(with = ColorSerializer::class) Color>
) : InstrumentObject, AbstractRenamableObject() {
    private val controllerName get() = "~ctrl_${name.now}"

    @Transient
    lateinit var outputSelector: BusSelector
        private set

    @Transient
    private lateinit var outputSelectorObserver: Observer

    @Transient
    private var isNew: Boolean = false

    @Transient
    private var controllerInfo: ControllerInfo? = null

    private val client get() = context[SuperColliderClient]

    fun initialize(context: Context, controllerInfo: ControllerInfo) {
        this.controllerInfo = controllerInfo
        initialize(context)
    }

    fun initializeNew(context: Context) {
        isNew = true
        initialize(context)
    }

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        outputSelector = BusSelector(context, Rate.Audio, 2, output)
        outputSelectorObserver = outputSelector.result.observe { _, _, new -> output = new }
        if (client.eval("s.hasBooted").join() == "true") this.client.loadVSTPlugin()
    }

    fun SuperColliderContext.loadVSTPlugin() {
        run {
            appendBlock("if (s.hasBooted)") {
                val info = controllerInfo
                if (info != null) {
                    +"$controllerName = VSTPluginController(${info.synthName}, \\${info.id})"
                } else {
                    val synthName = "~tmp_synth"
                    +"$synthName = Synth(\\vst_instrument, [out: ${output.get().variableName}])"
                    +"$controllerName = VSTPluginController($synthName)"
                }
                val action = if (isNew) "{}" else "_.loadPreset('$presetName')"
                +"$controllerName.open('$pluginName', action: $action)"
            }
            appendLine(";")
        }
    }

    override fun canRenameTo(newName: String): Boolean =
        !(context[InstrumentRegistry].has(name.now) && context[InstrumentRegistry].has(newName))

    override fun rename(newName: String) {
        val oldControllerName = controllerName
        super.rename(newName)
        client.run("$controllerName = $oldControllerName; $oldControllerName = nil;")
    }

    override fun SuperColliderClient.sync() {
        run("if ($controllerName != nil) { $controllerName.savePreset('$presetName') };")
    }

    override fun SuperColliderClient.remove() {
        run("$controllerName.synth.free; $controllerName = nil;")
    }

    fun showEditor() {
        client.run("$controllerName.editor;")
    }

    override fun createEvent(): Map<String, String> = mapOf("type" to "\\vst_midi", "vst" to controllerName)

    data class ControllerInfo(val synthName: String, val id: String)

    companion object {
        fun create(context: Context, name: String, pluginName: String): VSTPluginObject {
            val presetName = "${System.currentTimeMillis()}"
            val color = randomColor()
            val output = context[BusRegistry].getDefault().createReference()
            return VSTPluginObject(reactiveVariable(name), pluginName, presetName, output, reactiveVariable(color))
        }
    }
}