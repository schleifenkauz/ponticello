package xenakis.ui.score

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
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.binding.flatMap
import reaktive.value.binding.not
import reaktive.value.forEach
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.*
import xenakis.model.score.*
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.controls.ParameterControlList
import xenakis.ui.launcher.XenakisMainActivity

class SynthObjectView(
    instance: ScoreObjectInstance, override val obj: SynthObject
) : ParameterizedScoreObjectView<SynthObject>(instance), ParameterControls.Listener {
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

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        obj.controls.addListener(this)
        sampleObserver = obj.sample.forEach { s ->
            sampleContentObserver?.kill()
            if (s != null) {
                val sample = s.get()
                if (sample != null) {
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
            context[XenakisMainActivity].instrumentsPane.editInstrument(obj.synthDef!!)
        }.styleClass("medium-icon-button").disableIf(obj.synthDefSelector.isResolved.not())
        val box = ObjectSelectorControl(obj.synthDefSelector, createBundle())
        pane.addItem("SynthDef: ", HBox(5.0, box, viewBtn).centerChildren())
        pane.children.add(createDetailsHeader(obj, "Synth controls"))
        val controlList = ParameterControlList(obj.controls)
        controlList.addShortcutsTo(pane)
        pane.children.add(controlList.getContent())
    }

    override fun removedControl(control: ParameterControls.NamedParameterControl) {
        super.removedControl(control)
        if (control.now !is ConstantControl) return
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

    override fun addedControl(control: ParameterControls.NamedParameterControl, idx: Int) {
        super.addedControl(control, idx)
        val ctrl = control.now
        if (ctrl !is ConstantControl) return
        when (control.name.now) {
            "startPos" -> startPosObserver = ctrl.value.forEach { _ -> displaySpectrogram() }
            "rate" -> rateObserver = ctrl.value.forEach { _ -> displaySpectrogram() }
        }
    }

    override fun reassignedControl(
        namedControl: ParameterControls.NamedParameterControl,
        oldControl: ParameterControl,
        control: ParameterControl
    ) {

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
            spectrogramImage = sample?.spectrogramImage ?: return@runLater
            displaySpectrogram()
        }
    }

    private fun displaySpectrogram() {
        children.removeAll(spectrogramViews)
        spectrogramViews.clear()
        if (obj.displaySample?.now != true) return
        if (spectrogramImage == null) return
        val sample = obj.sample.now?.get() ?: return
        val rate = obj.playBufRate?.now ?: one(precision = 3)
        if (rate == zero) return
        val defaultStartPos = if (rate < zero) sample.duration else zero
        var startPos = obj.playbufStartPos?.now?.wrapAt(sample.duration) ?: defaultStartPos
        if (rate < zero && startPos < 1e-5.asTime) startPos = sample.duration
        var t = zero
        for (i in 0..100) {
            if (t >= obj.duration) break
            var imageDur = when {
                t > zero -> sample.duration / rate.abs()
                rate > zero -> (sample.duration - startPos) / rate
                else -> startPos / -rate
            }
            if (t + imageDur > obj.duration) imageDur = obj.duration - t
            val view = displaySpectrogramPart(
                imageDur, sample.duration, rate,
                startPos = if (t == zero) startPos else defaultStartPos
            )
            view.layoutX = pane.getWidth(t)
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
        view.fitWidth = pane.getWidth(duration)
        view.viewOrder = 1000.0
        if (rate < zero) view.transforms.addAll(
            Translate(view.fitWidth, 0.0),
            Scale(-1.0, 1.0),
        )
        return view
    }
}