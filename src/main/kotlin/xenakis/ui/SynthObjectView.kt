package xenakis.ui

import javafx.geometry.Point2D
import javafx.geometry.Rectangle2D
import javafx.scene.Cursor
import javafx.scene.control.ContextMenu
import javafx.scene.control.MenuItem
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.layout.HBox
import javafx.scene.paint.Color
import reaktive.Observer
import reaktive.value.ReactiveValue
import reaktive.value.forEach
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.*
import xenakis.sc.NumericalControlSpec

class SynthObjectView(val obj: SynthObject) : ScoreObjectView(obj), SynthControls.View {
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
        detailPane.addItem("Color:", colorPicker)
        obj.controls.addView(this)
        setupSynthDefReference()
        listenForMouseEvents()
        detailPane.addLargeItem("Synth controls", ControlAssignmentView(obj))
        sampleObserver = obj.sample.forEach { s ->
            sampleContentObserver?.kill()
            if (s != null) {
                sampleContentObserver = s.get().contentsChanged.observe { _ -> updateSpectrogram() }
                updateSpectrogram()
            }
        }
        sampleDisplayObserver = obj.displaySample?.forEach { updateSpectrogram() }
    }

    override fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor) {
        var newDuration = pane.getDuration(width)
        if (ev.isShiftDown && obj.playBufRate != null) {
            obj.playBufRate!!.now *= (obj.duration / newDuration)
        } else if (obj.playbufStartPos != null) {
            newDuration = pane.getDuration(width)
            if (cursor in setOf(Cursor.W_RESIZE, Cursor.SW_RESIZE, Cursor.NW_RESIZE)) {
                newDuration = newDuration.coerceAtMost(obj.duration + obj.playbufStartPos!!.now)
                val deltaStart = obj.duration - newDuration
                obj.playbufStartPos!!.now = (obj.playbufStartPos!!.now + deltaStart * (obj.playBufRate?.now ?: 1.0))
            }
        }
        super.resizeObject(pane.getWidth(newDuration), height, ev, cursor)
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
            "rate" -> startPosObserver = control.value.forEach { _ -> displaySpectrogram() }
        }
    }

    override fun rescale() {
        super.rescale()
        displaySpectrogram()
    }

    private fun updateSpectrogram() {
        envelopesPane.children.removeAll(spectrogramViews)
        spectrogramViews.clear()
        if (obj.displaySample?.now != true) return
        val imageFile = obj.sample.now?.get()?.spectrogramFile ?: return
        if (!imageFile.isFile) return
        image = Image(imageFile.inputStream())
        displaySpectrogram()
    }

    private fun displaySpectrogram() {
        envelopesPane.children.removeAll(spectrogramViews)
        spectrogramViews.clear()
        if (image == null) return
        val sample = obj.sample.now?.get() ?: return
        val startPos = obj.playbufStartPos?.now ?: 0.0
        val rate = obj.playBufRate?.now ?: 1.0
        val bufDur = (sample.duration - startPos) / rate
        var remainingDuration = obj.duration
        while (remainingDuration != 0.0) {
            val imageDur =
                minOf(remainingDuration, if (remainingDuration == obj.duration) bufDur else sample.duration / rate)
            val view = ImageView(image)
            displaySpectrogram(
                view, imageDur,
                sample.duration, rate, startPos = if (remainingDuration == obj.duration) startPos else 0.0
            )
            view.viewOrder = 100.0
            view.layoutX = pane.getWidth(obj.duration - remainingDuration)
            remainingDuration -= imageDur
            spectrogramViews.add(view)
        }
        envelopesPane.children.addAll(spectrogramViews)
    }

    private fun displaySpectrogram(
        view: ImageView, duration: Double,
        sampleDuration: Double, rate: Double, startPos: Double
    ) {
        val pixelsPerSecond = image!!.width / sampleDuration * rate
        val minX = pixelsPerSecond * startPos
        val minY = 0.0
        val width = pixelsPerSecond * duration
        val height = image!!.height
        view.viewport = Rectangle2D(minX, minY, width, height)
        view.fitHeight = prefHeight
        view.fitWidth = pane.getWidth(duration)
    }

    private fun setupSynthDefReference() {
        val nameLabel = label(obj.synthDef.name)
        val viewBtn = Icon.View.button(action = "View SynthDef") {
            context[InstrumentRegistryPane].editSynthDef(obj.synthDef)
        }
        val box = HBox(5.0, nameLabel, viewBtn).centerChildrenVertically()
        detailPane.addItem("SynthDef: ", box)
    }

    private fun listenForMouseEvents() {
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            if (ev.isAltDown) {
                val p = localToScreen(ev.x, ev.y)
                showNewEnvelopePopup(p)
                ev.consume()
            }
        }
    }

    private fun showNewEnvelopePopup(point: Point2D) {
        val possibleParameters = obj.synthDef.parameters.now
            .filter { p -> p.spec.now is NumericalControlSpec }
            .filter { p ->
                val control = obj.controls[p.name.now]
                control !is EnvelopeControl || !control.display.now
            }
        val menu = ContextMenu()
        for (p in possibleParameters) {
            val name = p.name.now
            val spec = p.spec.now as NumericalControlSpec
            val item = MenuItem(name)
            item.setOnAction {
                val oldControl = obj.controls[p.name.now]
                val env =
                    if (oldControl is EnvelopeControl) oldControl.envelope
                    else Envelope.constant(spec.defaultValue.get(), obj.duration, spec.warp)
                val control = EnvelopeControl(
                    env, reactiveVariable(spec.associatedColor),
                    display = reactiveVariable(true)
                )
                obj.controls.reassignControl(name, control)
            }
            menu.items.add(item)
        }
        menu.isAutoHide = true
        menu.show(scene.window, point.x, point.y)
    }

    override val defaultBackgroundColor: ReactiveValue<Color>
        get() = obj.synthDef.color
}