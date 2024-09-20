package xenakis.ui

import bundles.createBundle
import javafx.application.Platform
import javafx.geometry.Point2D
import javafx.geometry.Rectangle2D
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import javafx.scene.transform.Scale
import javafx.scene.transform.Translate
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.forEach
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.*
import xenakis.sc.NumericalControlSpec
import xenakis.sc.view.ObjectSelectorControl
import kotlin.math.absoluteValue

class SynthObjectView(
    instance: ScoreObjectInstance, val obj: SynthObject
) : ScoreObjectView(instance), SynthControls.View {
    private var image: Image? = null
    private val spectrogramViews = mutableListOf<ImageView>()

    private var startPosObserver: Observer? = null
    private var rateObserver: Observer? = null
    private var sampleObserver: Observer? = null
    private var sampleDisplayObserver: Observer? = null
    private var sampleContentObserver: Observer? = null

    init {
        styleClass("synth-object")
    }

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        obj.controls.addView(this)
        listenForMouseEvents()
        addAction(Icon.Reverse, action = "Reverse") { obj.reverse() }
        sampleObserver = obj.sample.forEach { s ->
            sampleContentObserver?.kill()
            if (s != null) {
                sampleContentObserver = s.get<SampleObject>().contentsChanged.observe { _ -> updateSpectrogram() }
                updateSpectrogram()
            }
        }
        sampleDisplayObserver = obj.displaySample?.forEach { updateSpectrogram() }
    }

    override fun DetailPane.setupDetailPane() {
        addItem("Color:", colorPicker)
        val viewBtn = Icon.View.button(action = "View SynthDef") {
            context[InstrumentRegistryPane].editInstrument(obj.synthDef)
        }
        val box = ObjectSelectorControl(obj.synthDefSelector, createBundle())
        addItem("SynthDef: ", HBox(5.0, box, viewBtn).centerChildrenVertically())
        val header = HBox(
            5.0,
            Label("Synth controls").styleClass("heading"),
            infiniteSpace(),
            Icon.Add.button(action = "Add control") { ev ->
                if (ev.isShiftDown) {
                    addControlsForAllSynthParameters()
                } else {
                    addNewControl(box)
                }
            }
        ) styleClass "tool-pane-header"
        children.addAll(header, ControlAssignmentView(obj))

    }

    private fun addControlsForAllSynthParameters() {
        for (param in obj.synthDef.parameters.now) {
            val name = param.name.now
            if (name !in obj.controls.controlMap) {
                obj.controls.addControl(name, param.defaultControl(context), extraSpec = null)
            }
        }
    }

    private fun addNewControl(anchorNode: Node) {
        val listView = SearchableParameterListView(context, obj.synthDef.parameters.now)
        val assignedParameters = obj.controls.controlMap.keys
            .map { key -> ParameterDefObject(key, obj.getSpec(key)) }
        listView.removedOptions.addAll(assignedParameters)
        listView.showPopup(context, "Add control", anchorNode = anchorNode) { option ->
            val extraSpec = option.spec.now.takeIf {
                option.name.now !in obj.synthDef.parameters.now.map { p -> p.name.now }
            }
            obj.controls.addControl(option.name.now, option.defaultControl(context), extraSpec)
        }
    }

    override fun removedControl(parameter: String, control: ParameterControl) {
        super.removedControl(parameter, control)
        if (control !is ConstantControl) return
        when (parameter) {
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

    override fun addedControl(parameter: String, control: ParameterControl) {
        super.addedControl(parameter, control)
        if (control !is ConstantControl) return
        when (parameter) {
            "startPos" -> startPosObserver = control.value.forEach { _ -> displaySpectrogram() }
            "rate" -> rateObserver = control.value.forEach { _ -> displaySpectrogram() }
        }
    }

    override fun rescale() {
        super.rescale()
        displaySpectrogram()
    }

    private fun updateSpectrogram() {
        Platform.runLater {
            envelopesPane.children.removeAll(spectrogramViews)
            spectrogramViews.clear()
            if (obj.displaySample?.now != true) return@runLater
            val imageFile = obj.sample.now?.get<SampleObject>()?.spectrogramFile ?: return@runLater
            image = Image(imageFile.inputStream())
            displaySpectrogram()
        }
    }

    private fun displaySpectrogram() {
        envelopesPane.children.removeAll(spectrogramViews)
        spectrogramViews.clear()
        if (obj.displaySample?.now != true) return
        if (image == null) return
        val sample = obj.sample.now?.get<SampleObject>() ?: return
        val rate = obj.playBufRate?.now ?: 1.0
        if (rate == 0.0) return
        val defaultStartPos = if (rate < 0) sample.duration else 0.0
        val startPos = obj.playbufStartPos?.now ?: defaultStartPos
        var t = 0.0
        for (i in 0..100) {
            if (t >= obj.duration) break
            var imageDur = if (t == 0.0) {
                if (rate < 0) startPos / -rate else (sample.duration - startPos) / rate
            } else sample.duration / rate.absoluteValue
            if (t + imageDur > obj.duration) imageDur = obj.duration - t
            val view = ImageView(image)
            displaySpectrogram(
                view, imageDur, sample.duration, rate,
                startPos = if (t == 0.0) startPos else defaultStartPos
            )
            view.layoutX = pane.getWidth(t)
            t += imageDur
            spectrogramViews.add(view)
        }
        envelopesPane.children.addAll(spectrogramViews)
    }

    private fun displaySpectrogram(
        view: ImageView, duration: Double,
        sampleDuration: Double, rate: Double, startPos: Double
    ) {
        val pixelsPerSecond = image!!.width / sampleDuration * rate.absoluteValue
        var minX = (image!!.width / sampleDuration) * startPos
        val minY = 0.0
        val width = pixelsPerSecond * duration
        if (rate < 0.0) minX -= width
        val height = image!!.height
        if (width < 0 || height < 0) return
        view.viewport = Rectangle2D(minX, minY, width, height)
        view.fitHeight = prefHeight
        view.fitWidth = pane.getWidth(duration)
        view.viewOrder = 1000.0
        if (rate < 0) view.transforms.addAll(
            Translate(prefWidth, 0.0),
            Scale(-1.0, 1.0),
        )
    }

    private fun listenForMouseEvents() {
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            if (ev.isAltDown) {
                val p = Point2D(ev.screenX, ev.screenY)
                showNewEnvelopePopup(p)
                ev.consume()
            }
        }
    }

    private fun showNewEnvelopePopup(point: Point2D) {
        val allParameters = obj.synthDef.parameters.now + obj.controls.extraParameters
        val possibleParameters = allParameters
            .filter { p -> p.spec.now is NumericalControlSpec }
            .filter { p ->
                val control = obj.controls.controlMap[p.name.now]
                control != null && (control !is EnvelopeControl || !control.display.now)
            }
        val listView = SearchableParameterListView(context, possibleParameters)
        listView.showPopup(context, "Add envelope", point) { param ->
            val name = param.name.now
            val spec = param.spec.now as NumericalControlSpec
            val env = Envelope.constant(spec.defaultValue.get(), obj.duration, spec.warp)
            val control = EnvelopeControl(
                env, reactiveVariable(spec.associatedColor),
                display = reactiveVariable(true)
            )
            val extraSpec = param.spec.now.takeIf {
                name !in obj.synthDef.parameters.now.map { p -> p.name.now }
            }
            obj.controls.reassignControl(name, control, extraSpec)
        }
    }

    override val defaultBackgroundColor: ReactiveValue<Color>
        get() = obj.synthDef.color
}