package xenakis.sc.editor

import hextant.core.editor.CompoundEditor
import reaktive.value.ReactiveValue
import xenakis.impl.Logger
import xenakis.impl.superColliderPath
import xenakis.model.project.XenakisProject.Companion.projectDirectory
import xenakis.sc.VSTPlugin
import xenakis.sc.client.SuperColliderClient

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