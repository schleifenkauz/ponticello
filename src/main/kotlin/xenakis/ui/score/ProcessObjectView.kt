package xenakis.ui.score

import bundles.createBundle
import fxutils.centerChildren
import fxutils.prompt.DetailPane
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import org.kordamp.ikonli.material2.Material2AL
import reaktive.value.ReactiveValue
import xenakis.model.score.ProcessObject
import xenakis.model.score.ScoreObjectInstance
import xenakis.sc.editor.ProcessDefSelector
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.actions.button
import xenakis.ui.launcher.XenakisMainActivity

class ProcessObjectView(
    instance: ScoreObjectInstance, override val obj: ProcessObject
) : ParameterizedScoreObjectView(instance) {
    override val defaultBackgroundColor: ReactiveValue<Color>
        get() = obj.processDef.color

    override fun setupDetailPane(pane: DetailPane) {
        pane.addItem("Color:", this.colorPicker)
        val viewBtn = Material2AL.CODE.button(action = "View SynthDef") {
            context[XenakisMainActivity].processDefsPane.editProcessDef(obj.processDef)
        }
        val selector = ProcessDefSelector(context, obj.processDefRef)
        val box = ObjectSelectorControl(selector, createBundle())
        pane.addItem("ProcessDef: ", HBox(5.0, box, viewBtn).centerChildren())
        super.setupDetailPane(pane)
    }
}