package xenakis.ui

import hextant.context.Context
import hextant.context.createControl
import hextant.fx.hbox
import hextant.serial.makeRoot
import javafx.application.Platform
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue
import reaktive.value.now
import xenakis.model.EnvelopeObject
import xenakis.sc.Bus
import xenakis.sc.NumericalControlSpec
import xenakis.sc.editor.BusRefEditor
import xenakis.sc.editor.IdentifierEditor
import xenakis.sc.editor.createEditor
import xenakis.sc.view.IdentifierEditorControl

class EnvelopeObjectView(val obj: EnvelopeObject) : ScoreObjectView(obj) {
    override val supportedActions: List<Icon>
        get() = listOf(Icon.Delete, Icon.Repeat, Icon.ExtraWindow)

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        val btn = Icon.Details.button(action = "Edit Envelope configuration") {
            showEnvelopeConfig(context, obj.name, obj.spec, obj.bus) { name, spec, output ->
                obj.name = name
                obj.spec = spec
                obj.bus = output
            }
        }
        header.children.add(1, btn)
    }

    fun updatedSpec() {
        reassignedControls()
    }

    companion object {
        fun showEnvelopeConfig(
            context: Context,
            initialName: String = "",
            initialSpec: NumericalControlSpec = NumericalControlSpec.DEFAULT,
            initialBus: Bus = Bus.output,
            onConfirm: (name: String, spec: NumericalControlSpec, outputBus: Bus) -> Unit
        ) {
            val nameEditor = IdentifierEditor(context, initialName)
            nameEditor.makeRoot()
            val specEditor = initialSpec.createEditor(context)
            specEditor.makeRoot()
            val outputBusEditor = BusRefEditor(context, initialBus)
            outputBusEditor.makeRoot()
            val nameControl = IdentifierEditorControl(nameEditor)
            val specControl = context.createControl(specEditor)
            val outputBusControl = context.createControl(outputBusEditor)
            nameControl.startEdit()
            val complete = Icon.Check.button(action = "Confirm name and control spec") {
                nameControl.scene.window.hide()
                val name = nameEditor.result.now.text
                val spec = specEditor.result.now
                val outputBus = outputBusEditor.result.now
                onConfirm(name, spec, outputBus)
            }
            complete.disableProperty().bind(nameEditor.result.map { name -> !name.isValid }.asObservableValue())
            val layout = hbox(nameControl, specControl, outputBusControl, complete) {
                spacing = 5.0
                centerChildrenVertically()
            }
            layout.minWidth = 600.0
            val window = SubWindow(layout, title = "Envelope config", context, type = SubWindow.Type.Prompt)
            window.show()
            Platform.runLater { nameControl.requestFocus() }
        }
    }
}