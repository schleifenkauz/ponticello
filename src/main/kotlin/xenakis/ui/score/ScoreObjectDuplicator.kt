package xenakis.ui.score

import bundles.PublicProperty
import bundles.publicProperty
import javafx.geometry.Point2D
import javafx.geometry.Rectangle2D
import javafx.scene.SnapshotParameters
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.stage.Window
import reaktive.event.event
import reaktive.event.unitEvent
import reaktive.value.now
import xenakis.impl.asY
import xenakis.model.obj.SampleObject
import xenakis.model.project.UI_STATE
import xenakis.model.project.get
import xenakis.model.registry.ScoreObjectRegistry
import xenakis.model.score.ScoreObject
import xenakis.model.score.SynthObject
import xenakis.ui.launcher.XenakisLauncher.Companion.currentProject

class ScoreObjectDuplicator {
    private val enterDuplicateMode = event<Image>()
    private val exitDuplicateMode = unitEvent()

    val onEnterDuplicateMode get() = enterDuplicateMode.stream
    val onExitDuplicateMode get() = exitDuplicateMode.stream

    var clipboardObject: ScoreObject? = null
        private set

    fun enterDuplicateMode(obj: ScoreObject, view: ScoreObjectView) {
        clipboardObject = obj
        val parameters = SnapshotParameters()
        parameters.viewport = Rectangle2D(5.0, 3.0, view.prefWidth - 4.0, view.prefHeight - 4.0)
        view.snapshot(parameters, null)
        val image = view.snapshot(parameters, null)
        enterDuplicateMode.fire(image)
    }

    fun enterDuplicateMode(sample: SampleObject, anchor: Point2D, window: Window) {
        val image = Image(sample.spectrogramFile.inputStream())
        val context = sample.context
        val synthDef = context[currentProject][UI_STATE].getOrSelectSynthDef(anchor, window) ?: return
        val controls = synthDef.getDefaultControls(null)
        val name = context[ScoreObjectRegistry].availableName(sample.name.now)
        val obj = SynthObject.create(name, synthDef, controls)
        obj.setInitialSize(sample.duration.now, 0.02.asY)
        clipboardObject = obj
        enterDuplicateMode.fire(image)
    }

    fun createImageView(image: Image) = ImageView(image).apply {
        opacity = 0.3
        viewOrder = -1000.0
        isMouseTransparent = true
    }

    fun exitDuplicateMode() {
        clipboardObject = null
        exitDuplicateMode.fire()
    }

    fun isInDuplicateMode() = clipboardObject != null

    companion object : PublicProperty<ScoreObjectDuplicator> by publicProperty("ScoreObjectDuplicator")
}