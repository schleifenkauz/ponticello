package xenakis.sc.view

import bundles.Bundle
import bundles.createBundle
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.context.createControl
import hextant.core.Editor
import hextant.core.view.EditorControl
import hextant.core.view.ListEditorView
import hextant.fx.PseudoClasses
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import reaktive.event.observe
import reaktive.value.now
import xenakis.impl.UDPSuperColliderClient
import xenakis.model.SynthDefs
import xenakis.sc.Identifier
import xenakis.sc.editor.IdentifierEditor
import xenakis.sc.editor.SynthDefEditor
import xenakis.ui.*

class SynthDefsEditorControl @ProvideImplementation(ControlFactory::class) constructor(
    val editor: xenakis.sc.editor.SynthDefListEditor,
    arguments: Bundle
) : EditorControl<VBox>(editor, arguments), ListEditorView {
    constructor(editors: xenakis.sc.editor.SynthDefListEditor) : this(editors, createBundle())

    private var selectedBtn: Button? = null

    private val selectorButtons = mutableMapOf<SynthDefEditor, Button>()

    private val defs = VBox().styleClass("synth-def-list")

    init {
        editor.addView(this)
    }

    override fun createDefaultRoot(): VBox {
        val label = Label("Synth Definitions").styleClass("tool-pane-heading")
        val space = infiniteSpace()
        val addBtn = Icon.Add.button(action = "Add SynthDef") { addSynthDefEditor() }
        val reloadBtn = Icon.Repeat.button(action = "Reload SynthDefs") {
            val client = context[UDPSuperColliderClient]
            context[SynthDefs].reload(client)
        }
        val header = HBox(label, space, addBtn, reloadBtn).styleClass("tool-pane-header")
        header.alignment = Pos.CENTER_LEFT
        header.spacing = 5.0
        return VBox(header, defs).styleClass("tool-pane")
    }

    private fun addSynthDefEditor() {
        showTextPrompt("SynthDef name", "", context) { name ->
            if (!Identifier.isValid(name) || editor.result.now.any { def -> def.name.text == name }) {
                return@showTextPrompt false
            }
            val newEditor = SynthDefEditor(context, name = IdentifierEditor(context, name))
            editor.addLast(newEditor)
            if (editor.editors.now.size == 1) {
                val selector = selectorButtons[newEditor]!!
                select(selector, newEditor)
            }
            true
        }
    }

    fun onSelected(synthDefName: String) {
        if (synthDefName == "default") return
        val editor = editor.editors.now.find { it.name.text.now == synthDefName }
            ?: error("SynthDef $synthDefName not found")
        val selector = selectorButtons[editor] ?: error("selector button for SynthDef $synthDefName not found")
        select(selector, editor)
    }

    override fun added(editor: Editor<*>, idx: Int) {
        editor as SynthDefEditor
        val control = context.createControl(editor)
        val selector = Button().styleClass("selector-button")
        selectorButtons[editor] = selector
        selector.setOnAction {
            if (selector == selectedBtn) return@setOnAction
            select(selector, editor)
        }
        val name = IdentifierEditorControl(editor.name)
        name.userData = name.onChangeCommited.observe { oldName: String, newName: String ->
            showYesNoDialog("Rename references", default = true)
            context[XenakisController.currentProject].renamedSynthDef(oldName, newName)
        }
        val colorLabel = Label("color: ")
        val colorControl = context.createControl(editor.associatedColor)
        val remove = Icon.Delete.button(action = "Remove this SynthDef") { this.editor.remove(editor) }
        val expand = Icon.Expand.button(action = "Show SynthDef details")
        val collapse = Icon.Collapse.button(action = "Hide SynthDef details")
        val header = HBox(
            selector, name, colorLabel, colorControl,
            infiniteSpace(), remove, collapse
        ).styleClass("synth-def-header")
        val box = VBox(header, control).styleClass("synth-def-box")
        expand.setOnAction {
            box.children.add(control)
            header.children.replaceAll { c -> if (c == expand) collapse else c }
        }
        collapse.setOnAction {
            box.children.remove(control)
            header.children.replaceAll { c -> if (c == collapse) expand else c }
        }
        addChild(control, idx)
        defs.children.add(idx, box)
    }

    private fun select(selector: Button, editor: SynthDefEditor) {
        selectedBtn?.pseudoClassStateChanged(PseudoClasses.SELECTED, false)
        selector.pseudoClassStateChanged(PseudoClasses.SELECTED, true)
        selectedBtn = selector
        context[SynthDefs].selectedSynthDef = editor.result.now
    }

    override fun removed(idx: Int) {
        defs.children.removeAt(idx)
    }

    override fun empty() {}

    override fun notEmpty() {}
}