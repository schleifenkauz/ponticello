package xenakis.model

import hextant.context.Context
import javafx.scene.paint.Color
import kotlinx.serialization.Transient
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.SuperColliderClient
import xenakis.impl.randomColor
import xenakis.sc.editor.AbstractRenamableObject

class VSTPluginObject private constructor(
    override val mutableName: ReactiveVariable<String>,
    private val pluginName: String,
    private val presetName: String,
    override val color: ReactiveVariable<Color>,
) : InstrumentObject, AbstractRenamableObject() {
    private val client get() = context[SuperColliderClient]

    private val controllerName get() = "~ctrl_${name.now}"

    @Transient
    private var controllerInfo: ControllerInfo? = null

    fun initialize(context: Context, controllerInfo: ControllerInfo) {
        this.controllerInfo = controllerInfo
        initialize(context)
    }

    override fun initialize(context: Context) {
        if (initialized) return
        super.initialize(context)
        client.run {
            val info = controllerInfo
            if (info != null) {
                +"$controllerName = VSTPluginController(${info.synthName}, \\${info.id})"
            } else {
                val synthName = "~tmp_synth"
                +"$synthName = Synth(\\vst_instrument)"
                +"$controllerName = VSTPluginController($synthName)"
            }
            +"$controllerName.open('$pluginName', action: _.loadPreset('$presetName'))"
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
        client.run("$controllerName.savePreset('$presetName');")
    }

    override fun SuperColliderClient.remove() {
        client.run("$controllerName.synth.free; $controllerName = nil;")
    }

    fun showEditor() {
        client.run("$controllerName.editor;")
    }

    override fun createEvent(): Map<String, String> = mapOf("type" to "vst_midi", "vst" to controllerName)

    data class ControllerInfo(val synthName: String, val id: String)

    companion object {
        fun create(name: String, pluginName: String): VSTPluginObject {
            val presetName = "${System.currentTimeMillis()}"
            val color = randomColor()
            return VSTPluginObject(reactiveVariable(name), pluginName, presetName, reactiveVariable(color))
        }
    }

}