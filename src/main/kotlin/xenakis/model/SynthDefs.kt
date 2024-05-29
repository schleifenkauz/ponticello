package xenakis.model

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.context.createControl
import hextant.serial.EditorRoot
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.impl.SuperColliderContext
import xenakis.sc.SynthDef
import xenakis.sc.editor.SynthDefListEditor
import xenakis.sc.view.SynthDefsEditorControl

@Serializable
class SynthDefs private constructor(
    val editor: EditorRoot<SynthDefListEditor>,
    private var selectedSynthDefName: String = SynthDef.default.name.text
) {
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

    fun get(name: String): SynthDef =
        if (name == "default") SynthDef.default
        else list.find { it.name.text == name } ?: error("no SynthDef with name '$name'")

    fun reload(context: SuperColliderContext) = context.postAsync {
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