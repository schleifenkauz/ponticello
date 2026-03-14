package ponticello.ui.registry

import fxutils.actions.action
import fxutils.actions.makeButton
import fxutils.infiniteSpace
import hextant.core.view.CompoundEditorControl
import hextant.serial.EditorRoot
import javafx.scene.Node
import javafx.scene.layout.VBox
import org.kordamp.ikonli.materialdesign2.MaterialDesignC
import ponticello.model.instr.MidiEffectInstrument
import ponticello.sc.editor.CodeBlockEditor
import reaktive.value.binding.`if`
import reaktive.value.fx.asReactiveValue

class MidiEffectInstrumentPane(def: MidiEffectInstrument) : ParameterizedObjectDefPane<MidiEffectInstrument>(def) {
    private fun makeCodePane(component: EditorRoot<CodeBlockEditor>, name: String, arguments: List<String>): VBox {
        component.control.isVisible = false
        component.control.isManaged = false
        return CompoundEditorControl.vertical {
            horizontal {
                keyword(name); space()
                operator("|")
                for ((index, arg) in arguments.withIndex()) {
                    if (index != 0) operator(", ")
                    text(arg)
                }
                operator("|")
                add(infiniteSpace())
                add(collapseExpandAction.withContext(component.control).makeButton("medium-icon-button"))
            }
            add(component.control)
            styleClass("code-pane")
        }
    }

    override fun getContent(def: MidiEffectInstrument): Node {
        val startCodeControl = makeCodePane(def.start, "start", listOf("track", "controls"))
        val stopCodeControl = makeCodePane(def.stop, "stop", listOf("track", "controls"))
        val eventArguments = listOf("pitch", "velocity", "channel", "track", "controls", "src")
        val noteOnControl = makeCodePane(def.noteOn, "noteOn", eventArguments)
        val noteOffControl = makeCodePane(def.noteOff, "noteOff", eventArguments)
        val ccControl = makeCodePane(def.cc, "cc", eventArguments)
        return VBox(startCodeControl, stopCodeControl, noteOnControl, noteOffControl, ccControl)
    }

    override fun requestFocus() {
        def.start.control.requestFocus()
    }

    companion object {
        private val collapseExpandAction = action<Node>("Collapse/Expand") {
            icon { node ->
                `if`(
                    node.visibleProperty().asReactiveValue(),
                    then = { MaterialDesignC.CHEVRON_DOWN },
                    otherwise = { MaterialDesignC.CHEVRON_RIGHT })
            }
            executes { node ->
                node.isVisible = !node.isVisible
                node.isManaged = !node.isManaged
            }
        }
    }
}