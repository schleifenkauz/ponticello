package xenakis.ui.score

import fxutils.button
import fxutils.prompt.DetailPane
import hextant.undo.compoundEdit
import javafx.scene.paint.Color
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.asTime
import xenakis.impl.asY
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.Score.Companion.rootScore
import xenakis.model.score.ScoreObjectInstance
import xenakis.ui.registry.SimpleSearchableRegistryView

class UnresolvedScoreObjectView(instance: ScoreObjectInstance) : ScoreObjectView(instance) {
    override val defaultBackgroundColor: ReactiveValue<Color>
        get() = reactiveValue(Color.gray(0.5, 0.5))

    override fun setupDetailPane(pane: DetailPane) {
        val btn = button("Select object reference")
        btn.setOnMouseClicked {
            val obj = SimpleSearchableRegistryView(context[ScoreObjectRegistry], "Resolve object")
                .showPopup(anchorNode = btn) ?: return@setOnMouseClicked
            val instances = context[rootScore].allInstances().filterTo(mutableSetOf()) { inst ->
                !inst.ref.isResolved.now && inst.ref.getName() == this.instance.ref.getName()
            }
            context.compoundEdit("Select object for unresolved instance") {
                for (inst in instances) {
                    inst.replaceWith(obj)
                }
            }
        }
        pane.addItem("Object #${instance.ref.getName()} unresolved", btn)
    }

    override fun getDisplayHeight(): Double = getScreenY(0.02.asY)

    override fun getDisplayWidth(): Double = getWidth(0.5.asTime)
}