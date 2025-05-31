package ponticello.sc.editor

import hextant.core.editor.CompoundEditor
import ponticello.impl.Logger
import ponticello.impl.superColliderPath
import ponticello.model.project.PonticelloProject.Companion.projectDirectory
import ponticello.sc.VSTPlugin
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import ponticello.sc.client.run
import reaktive.value.ReactiveValue

class VSTPluginEditor() : CompoundEditor<VSTPlugin>(), ScExprEditor<VSTPlugin> {
    constructor(pluginName: String) : this() {
        this.pluginName = pluginName
        this.presetName = System.currentTimeMillis().toString()
    }

    val input by child(ScExprExpander("nil"))
    val channels by child(SimpleIntegerEditor(2))
    val id by child(IdentifierEditor("vst"))

    lateinit var pluginName: String //TODO store presets in project directory
        private set

    var presetName: String? = null
        private set

    override lateinit var result: ReactiveValue<VSTPlugin>

    private val controllerVar get() = "~ctrl_$presetName"

    override fun doInitialize() {
        result = composeResult {
            VSTPlugin(input.get(), channels.get(), pluginName, id.get().text, presetName ?: "<no preset>")
        }
    }

    fun configurePlugin() {
        if (!checkControllerVar()) return
        context[SuperColliderClient].run("$controllerVar.editor")
    }

    private fun checkControllerVar(): Boolean {
        val controller = context[SuperColliderClient].eval(controllerVar).get()
        if (controller == "nil") {
            Logger.error("Plugin was not loaded", Logger.Category.VSTPlugins)
            return false
        }
        return true
    }

    fun saveConfiguration() {
        if (!checkControllerVar()) return
        val presetFile = context[projectDirectory].resolve("presets").resolve("$presetName.fxp")
        if (!presetFile.parentFile.isDirectory) presetFile.parentFile.mkdir()
        context[SuperColliderClient].run {
            +"~ctrl_$presetName.writeProgram(${presetFile.superColliderPath})"
        }
    }
}