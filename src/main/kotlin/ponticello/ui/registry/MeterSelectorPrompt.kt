package ponticello.ui.registry

import fxutils.hspace
import fxutils.infiniteSpace
import fxutils.label
import fxutils.prompt.CompoundPrompt
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import ponticello.model.obj.MeterObject
import ponticello.model.obj.withName
import ponticello.model.registry.MeterRegistry
import ponticello.ui.launcher.PonticelloApp.Companion.primaryStage
import ponticello.ui.score.TempoGridObjectView
import reaktive.value.binding.map

class MeterSelectorPrompt(
    registry: MeterRegistry, title: String,
) : RegistrySelectorPrompt<MeterObject>(registry, title) {
    override val canCreateItem: Boolean get() = true

    override fun createCell(option: MeterObject): Region = HBox(
        label(option.name), infiniteSpace(),
        label(option.beatsPerBar.map(Int::toString)), Label("x"), label(option.ticksPerBeat.map(Int::toString)),
        hspace(5.0), label(option.beatsPerMinute.map { bpm -> "(${bpm}bpm)" })
    )

    override fun createObject(name: String): MeterObject? {
        val meter = MeterObject.createDefault()
        MeterConfigDialog(meter, initialName = name).showDialog(registry.context[primaryStage], anchor) ?: return null
        return meter.withName(name)
    }

    class MeterConfigDialog(
        private val meter: MeterObject,
        initialName: String,
    ) : CompoundPrompt<MeterObject>("Configure meter") {
        private val nameField = TextField(initialName)

        init {
            addItem("Name: ", nameField)
            TempoGridObjectView.setupMeterConfig(meter, content, undoManager = null)
        }

        override fun onReceiveFocus() {
            nameField.requestFocus()
        }

        override fun confirm(): MeterObject {
            meter.setInitialName(nameField.text)
            return meter
        }
    }
}