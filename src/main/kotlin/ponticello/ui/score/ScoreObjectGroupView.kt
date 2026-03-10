package ponticello.ui.score

import bundles.createBundle
import fxutils.prompt.DetailPane
import fxutils.styleClass
import javafx.scene.layout.Background
import javafx.scene.layout.BackgroundFill
import javafx.scene.layout.CornerRadii
import ponticello.model.score.ScoreObjectGroup
import ponticello.model.score.ScoreObjectInstance
import ponticello.sc.view.ObjectSelectorControl
import ponticello.ui.midi.MidiContext
import reaktive.value.binding.map
import reaktive.value.fx.asObservableValue

class ScoreObjectGroupView(
    override val obj: ScoreObjectGroup,
    inst: ScoreObjectInstance,
) : AbstractScoreObjectGroupView(inst) {
    override lateinit var scorePane: SubScorePane
        private set

    init {
        styleClass("sub-score")
    }

    override fun initialize() {
        super.initialize()
        scorePane = SubScorePane(instance, obj, this, context)
        scorePane.prefWidthProperty().bind(prefWidthProperty())
        scorePane.prefHeightProperty().bind(prefHeightProperty())
        scorePane.backgroundProperty().bind(backgroundColor.map { color ->
            Background(BackgroundFill(color, CornerRadii.EMPTY, null))
        }.asObservableValue())
        children.add(scorePane)
        scorePane.initialize()
    }

    override fun setupDetailPane(pane: DetailPane, midiContext: MidiContext?) {
        pane.addItem("Color:", this.colorPicker)
        pane.addItem("Default bus", ObjectSelectorControl(this.obj.busSelector, createBundle()))
    }

    override fun rescale() {
        super.rescale()
        scorePane.repaint()
    }
}