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
import javafx.scene.layout.Priority
import javafx.scene.layout.Region
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
    val editors: xenakis.sc.editor.SynthDefListEditor,
    arguments: Bundle
) : EditorControl<VBox>(editors, arguments), ListEditorView {
    constructor(editors: xenakis.sc.editor.SynthDefListEditor): this(editors, createBundle())

    private var selectedBtn: Button? = null

    private val defs = VBox().styleClass("synth-def-list")

    override fun createDefaultRoot(): VBox {
        val label = Label("Synth Definitions").styleClass("synth-defs-heading")
        val space = infiniteSpace()
        val addBtn = Icon.Add.button(action = "Add SynthDef") { addSynthDefEditor() }
        val reloadBtn = Icon.Repeat.button(action = "Reload SynthDefs") {
            val client = context[UDPSuperColliderClient]
            context[SynthDefs].reload(client)
        }
        val header = HBox(label, space, addBtn, reloadBtn).styleClass("synth-defs-header")
        header.alignment = Pos.CENTER_LEFT
        header.spacing = 5.0
        return VBox(header, defs).styleClass("synth-defs")
    }

    private fun addSynthDefEditor() {
        val name = showTextInputDialog("SynthDef name", context) { txt -> Identifier.isValid(txt) } ?: return
        val editor = SynthDefEditor(context, name = IdentifierEditor(context, name))
        editors.addLast(editor)
    }

    override fun added(editor: Editor<*>, idx: Int) {
        editor as SynthDefEditor
        val control = context.createControl(editor)
        val selector = Button().styleClass("synth-def-selector")
        if (editor.name.result.now.text == context[SynthDefs].selectedSynthDefName) {
            selector.pseudoClassStateChanged(PseudoClasses.SELECTED, true)
            selectedBtn = selector
        }
        selector.setOnAction {
            if (selector == selectedBtn) return@setOnAction
            selectedBtn?.pseudoClassStateChanged(PseudoClasses.SELECTED, false)
            selector.pseudoClassStateChanged(PseudoClasses.SELECTED, true)
            selectedBtn = selector
            context[SynthDefs].selectedSynthDefName = editor.name.result.now.text
        }
        val name = IdentifierEditorControl(editor.name)
        name.userData = name.onChangeCommited.observe { oldName: String, newName: String ->
            showYesNoDialog("Rename references", default = true)
            context[XenakisController.currentProject].renamedSynthDef(oldName, newName)
        }
        val remove = Icon.Delete.button(action = "Remove this SynthDef") { editors.remove(editor) }
        val expand = Icon.Expand.button(action = "Show SynthDef details")
        val collapse = Icon.Collapse.button(action = "Hide SynthDef details")
        val space = Region()
        val header = HBox(selector, name, space, remove, collapse).styleClass("synth-def-header")
        HBox.setHgrow(space, Priority.ALWAYS)
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

    override fun removed(idx: Int) {
        defs.children.removeAt(idx)
    }

    override fun empty() {}

    override fun notEmpty() {}
}