package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.context.createControl
import hextant.serial.EditorRoot
import javafx.scene.paint.Color
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.impl.*
import xenakis.sc.*
import xenakis.sc.editor.SynthDefListEditor
import xenakis.sc.view.SynthDefsEditorControl
import java.util.concurrent.CompletableFuture

@Serializable
class SynthDefs private constructor(
    val editor: EditorRoot<SynthDefListEditor>,
    private var selectedSynthDefName: String = SynthDef.default.name.text
) {
    private val client = editor.editor.context[SuperColliderClient]

    val list: List<SynthDef>
        get() = editor.editor.result.now

    var selectedSynthDef: SynthDef
        get() = get(selectedSynthDefName)
        set(value) {
            if (value.name.text == selectedSynthDefName) return
            selectedSynthDefName = value.name.text
            updateSelection(value)
        }

    private fun updateSelection(value: SynthDef) {
        val control = editor.control as SynthDefsEditorControl
        control.onSelected(value.name.text)
    }

    init {
        editor.editor.context[SynthDefs] = this
        updateSelection(selectedSynthDef)
    }

    fun synthDescLibContains(name: String): CompletableFuture<Boolean> {
        val answer = client.send("isSynthDef", listOf(name))
        return answer.thenApply { msg -> msg.boolean }
    }

    //SynthDef(\x, { \amp.kr(0.1, 0, false, [0.0, 1.0, 'lin', 0.1]) }).add;
    fun loadFromSynthDescLib(name: String): CompletableFuture<SynthDef> {
        if (name == "default") return CompletableFuture.completedFuture(SynthDef.default)
        return CompletableFuture.supplyAsync {
            val params = mutableListOf<ParameterDef>()
            val nParams = client.send("controls", listOf(name)).join().integer
            for (i in 0 until nParams) {
                val controlRef = listOf(name, i)
                val paramName = client.send("controlName", controlRef).join().string
                val type = client.send("controlType", controlRef).join().string
                val default = client.send("controlDefault", controlRef).join()
                val spec = when (type) {
                    "bus" -> BusControlSpec(Bus.output)
                    "buf" -> BufferControlSpec(NoBuffer)
                    "num" -> {
                        val min = client.send("controlMinval", listOf(name, paramName)).join().double
                        val max = client.send("controlMaxval", listOf(name, paramName)).join().double
                        val warp = client.send("controlWarp", listOf(name, paramName)).join().warp
                        val step = client.send("controlStep", listOf(name, paramName)).join().double
                        NumericalControlSpec(default.double, min, max, warp, step, Color.web(randomColor()))
                    }

                    else -> error("unknown control type: $type")
                }
                val param = ParameterDef(Identifier(paramName), spec)
                params.add(param)
            }
            val associatedColor = Color.web(randomColor())
            val ugenGraph = CodeBlock(emptyList(), emptyList())
            SynthDef(Identifier(name), params, ugenGraph, associatedColor)
        }
    }

    fun get(name: String): SynthDef =
        if (name == "default") SynthDef.default
        else list.find { it.name.text == name } ?: error("no SynthDef with name '$name'")

    fun reload(context: SuperColliderContext) = context.run {
        for (def in list) {
            def.code(this)
            appendLine(".add;")
        }
    }

    fun renamedSynthDef(oldName: String, newName: String) {
        if (selectedSynthDefName == oldName) {
            selectedSynthDefName = newName
        }
    }

    companion object : PublicProperty<SynthDefs> by publicProperty("SynthDefs") {
        fun newInstance(context: Context): SynthDefs {
            val editor = SynthDefListEditor(context)
            val control = context.createControl(editor)
            return SynthDefs(EditorRoot(editor, control))
        }
    }
}