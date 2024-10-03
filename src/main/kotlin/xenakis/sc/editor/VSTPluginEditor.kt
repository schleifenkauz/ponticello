package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.CompoundEditor
import hextant.serial.Snapshot
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import reaktive.value.ReactiveValue
import xenakis.impl.SuperColliderClient
import xenakis.impl.getString
import xenakis.impl.superColliderPath
import xenakis.model.Logger
import xenakis.model.XenakisProject.Companion.projectDirectory
import xenakis.sc.VSTPlugin

class VSTPluginEditor(context: Context) : CompoundEditor<VSTPlugin>(context), ScExprEditor<VSTPlugin> {
    constructor(context: Context, pluginName: String) : this(context) {
        this.pluginName = pluginName
        this.presetName = System.currentTimeMillis().toString()
    }

    val input by child(ScExprExpander(context, "nil"))
    val channels by child(SimpleIntegerEditor(context, 2))
    val id by child(IdentifierEditor(context, "vst"))

    lateinit var pluginName: String
        private set

    var presetName: String? = null
        private set

    override val result: ReactiveValue<VSTPlugin>
        get() = composeResult {
            VSTPlugin(input.get(), channels.get(), pluginName, id.get().text, presetName ?: "<no preset>")
        }

    private val controllerVar get() = "~ctrl_$presetName"

    fun configurePlugin() {
        if (!checkControllerVar()) return
        context[SuperColliderClient].run("$controllerVar.editor")
    }

    private fun checkControllerVar(): Boolean {
        val controller = context[SuperColliderClient].eval(controllerVar).join()
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

    override fun createSnapshot(): Snapshot<*> = Snap()

    private class Snap : CompoundEditor.Snap() {
        private var pluginName: String? = null
        private var presetName: String? = null

        override fun doRecord(original: CompoundEditor<*>) {
            original as VSTPluginEditor
            super.doRecord(original)
            pluginName = original.pluginName
            presetName = original.presetName
        }

        override fun reconstructObject(original: CompoundEditor<*>) {
            original as VSTPluginEditor
            super.reconstructObject(original)
            original.pluginName = pluginName ?: error("invalid snap")
            original.presetName = presetName ?: error("invalid snap")
        }

        override fun decode(element: JsonObject) {
            super.decode(element)
            pluginName = element.getString("_pluginName")
            presetName = element.getString("_presetName")
        }

        override fun encode(builder: JsonObjectBuilder) {
            super.encode(builder)
            builder.put("_pluginName", pluginName)
            builder.put("_presetName", presetName)
        }
    }
}