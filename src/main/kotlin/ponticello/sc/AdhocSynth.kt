package ponticello.sc

import hextant.codegen.Compound
import hextant.context.Context
import kotlinx.serialization.Serializable
import ponticello.impl.superColliderPath
import ponticello.model.project.PonticelloProject
import ponticello.sc.client.ScWriter

@Compound(nodeType = ScExpr::class)
@Serializable
data class AdhocSynth(
    val name: Identifier,
    val block: CodeBlock,
) : ScExpr {
    override val isValid: Boolean
        get() = block.isValid

    override val children: List<ScElement>
        get() = listOf(block)

    override fun code(writer: ScWriter, context: Context) = writeCode(
        writer, context,
        synthName = "~adhoc_${name.text}",
        target = "s.defaultGroup",
        addAction = "addToHead",
        wrapInTask = false
    )

    fun writeCode(
        writer: ScWriter,
        context: Context,
        synthName: String,
        target: String,
        addAction: String,
        wrapInTask: Boolean,
    ) = with(writer) {
        val plugins = block.statements.flatMap { s -> s.allChildren<VSTPlugin>() }
        if (wrapInTask && plugins.isNotEmpty()) {
            appendBlock("Task", endLine = false) {
                writeCodeInsideTask(context, plugins, synthName, target, addAction)
            }
            +".play"
        } else {
            writeCodeInsideTask(context, plugins, synthName, target, addAction)
        }
    }

    private fun ScWriter.writeCodeInsideTask(
        context: Context, plugins: List<VSTPlugin>,
        synthName: String, target: String, addAction: String,
    ) {
        appendBlock("$synthName = SynthDef(\\${name.text})", endLine = false) {
            block.writeCode(writer, context)
        }
        +".add.play($target, addAction: $addAction)"
        if (plugins.isEmpty()) return
        +"s.sync"
        +"1.wait"
        for (plugin in plugins) {
            val pluginName = plugin.pluginName
            val presetName = plugin.presetName
            val presetFile = context[PonticelloProject.projectDirectory].resolve("presets").resolve("$presetName.fxp").superColliderPath
            val action = "action: { |c| if (PathName(${presetFile}).isFile) { c.readProgram(${presetFile}) } }"
            +"~ctrl_$presetName = VSTPluginController($synthName, id: '${plugin.id}').open('$pluginName.vst3', $action)"
        }
    }
}