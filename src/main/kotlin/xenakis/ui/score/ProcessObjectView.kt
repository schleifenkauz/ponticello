package xenakis.ui.score

import bundles.createBundle
import fxutils.actions.button
import fxutils.centerChildren
import fxutils.disableIf
import fxutils.prompt.DetailPane
import fxutils.styleClass
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import org.kordamp.ikonli.material2.Material2AL
import reaktive.value.ReactiveValue
import reaktive.value.binding.flatMap
import reaktive.value.binding.not
import reaktive.value.reactiveValue
import xenakis.model.score.ProcessObject
import xenakis.model.score.ScoreObjectInstance
import xenakis.sc.editor.ProcessDefSelector
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.launcher.XenakisMainActivity

class ProcessObjectView(
    override val obj: ProcessObject, instance: ScoreObjectInstance,
) : ParameterizedScoreObjectView<ProcessObject>(instance) {
    private lateinit var controlsPane: ParameterControlsPane

    override val defaultBackgroundColor: ReactiveValue<Color>
        get() = obj.processDefRef.flatMap { ref -> ref.get()?.color ?: reactiveValue(Color.GRAY) }

    override fun initialize() {
        super.initialize()
        controlsPane = ParameterControlsPane(obj, "Process controls", this)
    }

    override fun setupDetailPane(pane: DetailPane) {
        pane.addItem("Color:", this.colorPicker)
        val selector = ProcessDefSelector()
        selector.syncWith(obj.processDefRef)
        selector.initialize(context)
        val viewBtn = Material2AL.CODE.button(action = "View ProcessDef") {
            context[XenakisMainActivity].processDefsPane().listView.showContent(obj.processDef)
        }.styleClass("medium-icon-button").disableIf(selector.isResolved.not())
        val box = ObjectSelectorControl(selector, createBundle())
        pane.addItem("ProcessDef: ", HBox(5.0, box, viewBtn).centerChildren())
        pane.children.add(controlsPane)
    }
}