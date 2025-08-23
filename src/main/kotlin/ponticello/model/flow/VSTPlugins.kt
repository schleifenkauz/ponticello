package ponticello.model.flow

import hextant.context.Context
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval
import java.io.File

object VSTPlugins {
    fun availablePlugins(context: Context) = context[SuperColliderClient]
        .eval(
            "var str = \"\"; VSTPlugin.pluginList.do { |p| str = str ++ \", \" ++ p.key }; str;",
            description = "getting list of available plugins"
        ).get()
        .removePrefix(", ")
        .split(", ")
        .toSet()

    fun globalPresetList(context: Context, pluginName: String): List<String> {
        val presets = context[SuperColliderClient]
            .eval("VSTPlugin.plugins['$pluginName'].presets.collect(_.name)").get()
            .replace(" ", "").removePrefix("[").removeSuffix("]")
            .ifEmpty { return emptyList() }
            .split(",")
        return presets
    }

    fun getPresetsFolder(context: Context, pluginName: String): File? {
        val path = context[SuperColliderClient].eval("VSTPlugin.plugins['$pluginName'].presetsFolder").get()
        return if (path == "nil") null else File(path)
    }

    fun hasInputs(pluginName: String, context: Context): Boolean =
        context[SuperColliderClient].eval("VSTPlugin.plugins['$pluginName'].inputs != nil")
            .get().toBooleanStrictOrNull() ?: true
}