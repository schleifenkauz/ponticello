package xenakis.sc.view

import bundles.Bundle
import bundles.createBundle
import hextant.codegen.ProvideImplementation
import hextant.context.ControlFactory
import hextant.context.EditorControlGroup
import hextant.context.createControl
import hextant.context.withoutUndo
import hextant.core.Editor
import hextant.core.editor.ColorEditor
import hextant.core.view.EditorControl
import hextant.core.view.ListEditorView
import hextant.fx.PseudoClasses
import hextant.fx.initHextantScene
import javafx.application.Platform
import javafx.geometry.Pos
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import reaktive.event.observe
import reaktive.value.now
import xenakis.impl.SuperColliderClient
import xenakis.impl.randomColor
import xenakis.model.SynthDefs
import xenakis.sc.Identifier
import xenakis.sc.SynthDef
import xenakis.sc.editor.CodeBlockEditor
import xenakis.sc.editor.IdentifierEditor
import xenakis.sc.editor.ParameterDefExpander
import xenakis.sc.editor.SynthDefEditor
import xenakis.ui.*
import java.util.concurrent.CompletableFuture

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
            val client = context[SuperColliderClient]
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
            CompletableFuture.supplyAsync {
                if (context[SynthDefs].synthDescLibContains(name).join()) {
                    Platform.runLater {
                        val add = showYesNoDialog(
                            "SynthDef '$name' is already defined in the global SynthDescLib. " +
                                    "Import SynthDef '$name' from SynthDescLib? (Should not be edited afterwards!)",
                            default = true
                        )
                        if (add) addSynthDefFromSynthDescLib(name)
                        else addNewSynthDef(name)
                    }
                } else Platform.runLater { addNewSynthDef(name) }
            }.exceptionally { ex -> ex.printStackTrace() }
            true
        }
    }

    private fun addNewSynthDef(name: String) {
        val newEditor = SynthDefEditor(context, name = IdentifierEditor(context, name))
        editor.addLast(newEditor)
        newEditor.associatedColor.setText(randomColor())
        if (editor.editors.now.size == 1) {
            val selector = selectorButtons[newEditor]!!
            select(selector, newEditor)
        }
        val control = context[EditorControlGroup].getViewOf(newEditor)
        openCodeEditor(newEditor, control)
    }

    private fun addSynthDefFromSynthDescLib(name: String) {
        CompletableFuture.supplyAsync {
            val def = context[SynthDefs].loadFromSynthDescLib(name).join()
            val defEditor = SynthDefEditor(
                context, IdentifierEditor(context, def.name.text),
                ugenGraph = CodeBlockEditor(context),
                associatedColor = ColorEditor(context, def.associatedColor)
            )
            context.withoutUndo {
                for (param in def.parameters) {
                    val paramEditor = ParameterDefExpander(context, param)
                    defEditor.parameters.addLast(paramEditor)
                }
            }
            Platform.runLater {
                this.editor.addLast(defEditor)
            }
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
        val edit = Icon.View.button(action = "Edit SynthDef") { openCodeEditor(editor, control) }
        val remove = Icon.Delete.button(action = "Remove this SynthDef") { this.editor.remove(editor) }
        val box = HBox(selector, name, colorLabel, colorControl, infiniteSpace(), edit, remove)
            .styleClass("synth-def-box")
        addChild(control, idx)
        defs.children.add(idx, box)
    }

    private fun openCodeEditor(editor: SynthDefEditor, control: EditorControl<*>) {
        val window = SubWindow(control, "Edit SynthDef ${editor.name.result.now.text}", context)
        window.width = 1000.0
        window.height = 1000.0
        window.scene.initHextantScene(context, applyStyle = false)
        window.show()
    }

    private fun select(selector: Button, editor: SynthDefEditor) {
        selectedBtn?.pseudoClassStateChanged(PseudoClasses.SELECTED, false)
        selector.pseudoClassStateChanged(PseudoClasses.SELECTED, true)
        selectedBtn = selector
        context[SynthDefs].selectedSynthDef = editor.result.now
    }

    override fun removed(idx: Int) {
        defs.children.removeAt(idx)
        try {
            context[SynthDefs].selectedSynthDef
        } catch (ex: IllegalStateException) {
            context[SynthDefs].selectedSynthDef = SynthDef.default
            selectedBtn = null
        }
    }

    override fun empty() {}

    override fun notEmpty() {}
}