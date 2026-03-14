package ponticello.model.flow

import hextant.context.Context
import ponticello.sc.client.SuperColliderClient
import ponticello.sc.client.eval

object VSTPlugins {
    fun availablePlugins(context: Context, midi: Boolean = false) = context[SuperColliderClient]
        .eval(
            "VSTPlugin.pluginListString(midi: $midi)",
            description = "getting list of available plugins"
        ).get()
        .removeSuffix(",")
        .split(",")
        .toSet()

    fun globalPresetList(context: Context, pluginName: String): List<String> {
        val presets = context[SuperColliderClient]
            .eval("VSTPlugin.plugins['$pluginName'].presets.collect(_.name)").get()
            .replace(" ", "").removePrefix("[").removeSuffix("]")
            .ifEmpty { return emptyList() }
            .split(",")
        return presets
    }

    fun hasInputs(pluginName: String, context: Context): Boolean =
        context[SuperColliderClient].eval("VSTPlugin.plugins['$pluginName'].inputs != nil")
            .get().toBooleanStrictOrNull() ?: true
}