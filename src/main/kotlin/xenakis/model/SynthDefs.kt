package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.serial.EditorRoot
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.impl.SuperColliderContext
import xenakis.sc.SynthDef
import xenakis.sc.editor.SynthDefListEditor
import xenakis.sc.view.SynthDefsEditorControl

@Serializable
class SynthDefs(
    val editor: EditorRoot<SynthDefListEditor>,
    var selectedSynthDefName: String = SynthDef.default.name.text
) {
    val list: List<SynthDef>
        get() = editor.editor.result.now

    val selectedSynthDef: SynthDef get() = get(selectedSynthDefName)

    init {
        editor.editor.context[SynthDefs] = this
        editor.editor.addView(editor.control as SynthDefsEditorControl)
    }

    fun get(name: String): SynthDef =
        if (name == "default") SynthDef.default
        else list.find { it.name.text == name } ?: error("no SynthDef with name '$name'")

    fun reload(context: SuperColliderContext) = context.postAsync {
        for (def in list) {
            def.code(this)
            appendLine(".add;")
        }
    }

    companion object : PublicProperty<SynthDefs> by publicProperty("SynthDefs")
}