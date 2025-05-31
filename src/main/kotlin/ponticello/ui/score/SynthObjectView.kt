package ponticello.ui.score

import bundles.createBundle
import fxutils.actions.button
import fxutils.centerChildren
import fxutils.disableIf
import fxutils.prompt.DetailPane
import fxutils.styleClass
import javafx.application.Platform
import javafx.geometry.Rectangle2D
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import javafx.scene.transform.Scale
import javafx.scene.transform.Translate
import org.kordamp.ikonli.material2.Material2AL
import ponticello.impl.*
import ponticello.model.obj.SampleObject
import ponticello.model.score.ParameterControlList
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.SynthObject
import ponticello.model.score.controls.ParameterControl
import ponticello.model.score.controls.ValueControl
import ponticello.sc.view.ObjectSelectorControl
import ponticello.ui.launcher.PonticelloMainActivity
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.binding.flatMap
import reaktive.value.binding.not
import reaktive.value.forEach
import reaktive.value.now
import reaktive.value.reactiveValue

class SynthObjectView(
    override val obj: SynthObject, instance: ScoreObjectInstance
) : ParameterizedScoreObjectView<SynthObject>(instance), ParameterControlList.Listener {
    private var spectrogramImage: Image? = null
    private val spectrogramViews = mutableListOf<ImageView>()

    private var startPosObserver: Observer? = null
    private var rateObserver: Observer? = null
    private var sampleObserver: Observer? = null
    private var sampleDisplayObserver: Observer? = null
    private var sampleContentObserver: Observer? = null

    override val defaultBackgroundColor: ReactiveValue<Color>
        get() = obj.synthDefSelector.result.flatMap { ref -> ref.get()?.color ?: reactiveValue(Color.GRAY) }

    init {
        styleClass("synth-object")
    }

    override fun initialize() {
        super.initialize()
        sampleObserver = obj.sample.forEach { s ->
            sampleContentObserver?.kill()
            if (s != null) {
                val sample = s.get()
                if (sample is SampleObject) {
                    sampleContentObserver = sample.contentsChanged.observe { _ -> updateSpectrogram() }
                }
                updateSpectrogram()
            }
        }
        sampleDisplayObserver = obj.displaySample?.forEach { updateSpectrogram() }
    }

    override fun setupDetailPane(pane: DetailPane) {
        pane.addItem("Color:", this.colorPicker)
        val viewBtn = Material2AL.CODE.button(action = "View SynthDef") {
            context[PonticelloMainActivity].synthDefsPane().listView.showContent(obj.synthDef)
        }.styleClass("medium-icon-button").disableIf(obj.synthDefSelector.isResolved.not())
        val box = ObjectSelectorControl(obj.synthDefSelector, createBundle())
        pane.addItem("SynthDef: ", HBox(5.0, box, viewBtn).centerChildren())
        val controlsPane = ParameterControlsPane(obj, "Synth controls", this)
        controlsPane.listView.autoResizeScene = true
        pane.children.add(controlsPane)
    }

    override fun added(obj: ParameterControlList.NamedParameterControl, idx: Int) {
        super<ParameterizedScoreObjectView>.added(obj, idx)
        val ctrl = obj.now
        if (ctrl !is ValueControl) return
        addedConstantControl(obj, ctrl)
    }

    private fun addedConstantControl(
        control: ParameterControlList.NamedParameterControl,
        ctrl: ValueControl
    ) {
        when (control.name.now) {
            "startPos" -> startPosObserver = ctrl.value.forEach { _ -> displaySpectrogram() }
            "rate" -> rateObserver = ctrl.value.forEach { _ -> displaySpectrogram() }
        }
    }

    override fun removed(control: ParameterControlList.NamedParameterControl) {
        super<ParameterizedScoreObjectView>.removed(control)
        if (control.now !is ValueControl) return
        removedConstantControl(control)
    }

    private fun removedConstantControl(control: ParameterControlList.NamedParameterControl) {
        when (control.name.now) {
            "startPos" -> {
                startPosObserver?.kill()
                displaySpectrogram()
            }

            "rate" -> {
                rateObserver?.kill()
                displaySpectrogram()
            }
        }
    }

    override fun reassignedControl(
        parameter: ParameterControlList.NamedParameterControl,
        oldControl: ParameterControl,
        newControl: ParameterControl
    ) {
        super.reassignedControl(parameter, oldControl, newControl)
        if (oldControl is ValueControl) {
            when (parameter.name.now) {
                "startPos" -> startPosObserver?.kill()
                "rate" -> rateObserver?.kill()
            }
        }
        if (newControl is ValueControl) addedConstantControl(parameter, newControl)
    }

    override fun rescale() {
        super.rescale()
        displaySpectrogram()
    }

    private fun updateSpectrogram() {
        Platform.runLater {
            children.removeAll(spectrogramViews)
            spectrogramViews.clear()
            if (obj.displaySample?.now != true) return@runLater
            val sample = obj.sample.now?.get()
            if (sample !is SampleObject) return@runLater
            spectrogramImage = sample.spectrogramImage
            displaySpectrogram()
        }
    }

    private fun displaySpectrogram() = Platform.runLater {
        children.removeAll(spectrogramViews)
        spectrogramViews.clear()
        if (obj.displaySample?.now != true) return@runLater
        if (spectrogramImage == null) return@runLater
        val sample = obj.sample.now?.get() as? SampleObject ?: return@runLater
        val rate = obj.playBufRate?.now ?: one(precision = 3)
        if (rate == zero) return@runLater
        val defaultStartPos = if (rate < zero) sample.duration().now else zero
        var startPos = obj.playbufStartPos?.now?.wrapAt(sample.duration().now) ?: defaultStartPos
        if (rate < zero && startPos < 1e-5.asTime) startPos = sample.duration().now
        var t = zero
        for (i in 0..100) {
            if (t >= obj.duration) break
            var imageDur = when {
                t > zero -> sample.duration().now / rate.abs()
                rate > zero -> (sample.duration().now - startPos) / rate
                else -> startPos / -rate
            }
            if (t + imageDur > obj.duration) imageDur = obj.duration - t
            val view = displaySpectrogramPart(
                imageDur, sample.duration().now, rate,
                startPos = if (t == zero) startPos else defaultStartPos
            )
            view.layoutX = getWidth(t)
            t += imageDur
            spectrogramViews.add(view)
        }
        children.addAll(spectrogramViews)
    }

    private fun displaySpectrogramPart(
        duration: Decimal,
        sampleDuration: Decimal,
        rate: Decimal,
        startPos: Decimal
    ): ImageView {
        val view = ImageView(spectrogramImage)
        val pixelsPerSecond = (spectrogramImage!!.width / sampleDuration * rate.absoluteValue).toDouble()
        var minX = ((spectrogramImage!!.width / sampleDuration) * startPos).toDouble()
        val minY = 0.0
        val width = (pixelsPerSecond * duration).toDouble()
        if (rate < zero) minX -= width
        val height = spectrogramImage!!.height
        view.viewport = Rectangle2D(minX, minY, width, height)
        view.fitHeight = prefHeight
        view.fitWidth = getWidth(duration)
        view.viewOrder = 1000.0
        if (rate < zero) view.transforms.addAll(
            Translate(view.fitWidth, 0.0),
            Scale(-1.0, 1.0),
        )
        return view
    }
}