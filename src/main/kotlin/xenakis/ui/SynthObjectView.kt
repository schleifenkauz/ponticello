package xenakis.ui

import bundles.createBundle
import hextant.undo.UndoManager
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
import reaktive.value.now
import xenakis.model.Envelope
import xenakis.model.EnvelopeControl
import xenakis.model.ParameterControl
import xenakis.model.SynthObject
import xenakis.sc.NumericalControlSpec
import xenakis.sc.view.ObjectSelectorControl
import xenakis.ui.XenakisController.Companion.currentProject

class SynthObjectView(val obj: SynthObject) : ScoreObjectView(obj) {
    private lateinit var image: Image
    private val spectrogramViews = mutableListOf<ImageView>()

    private var sampleContentsObserver: Observer? = null

    init {
        styleClass("synth-object")
    }

    override fun initialize(parent: ScorePane) {
        super.initialize(parent)
        val btn = Icon.Details.button(action = "Open control assignment view") { openControlAssignment() }
        header.children.add(1, colorPicker)
        header.children.add(1, btn)
        header.children.add(1, ObjectSelectorControl(obj.groupSelector, createBundle()))
        setupSynthDefReference()
        listenForMouseEvents()
        loadSpectrogram()
    }

    override fun resizeObject(width: Double, height: Double, ev: MouseEvent, cursor: Cursor) {
        var newDuration = pane.getDuration(width)
        if (ev.isShiftDown && obj.playBufRate != null) {
            obj.playBufRate = obj.playBufRate!! * (obj.duration / newDuration)
        } else if (obj.playbufStartPos != null) {
            newDuration = pane.getDuration(width)
            if (cursor in setOf(Cursor.W_RESIZE, Cursor.SW_RESIZE, Cursor.NW_RESIZE)) {
                newDuration = newDuration.coerceAtMost(obj.duration + obj.playbufStartPos!!)
                val deltaStart = obj.duration - newDuration
                obj.playbufStartPos = (obj.playbufStartPos!! + deltaStart * (obj.playBufRate ?: 1.0))
            }
        }
        super.resizeObject(pane.getWidth(newDuration), height, ev, cursor)
    }

    override fun removedControl(parameter: String, oldControl: ParameterControl) {
        super.removedControl(parameter, oldControl)
        if (parameter == "buf") {
            sampleContentsObserver?.kill()
            envelopesPane.children.removeAll(spectrogramViews)
        } else if (parameter == "startPos" || parameter == "rate") displaySpectrogram()
    }

    override fun addedControl(parameter: String, newControl: ParameterControl) {
        super.addedControl(parameter, newControl)
        if (parameter == "buf" && obj.sample != null) {
            sampleContentsObserver = obj.sample?.contentsChanged?.observe { _, _ -> loadSpectrogram() }
            loadSpectrogram()
        } else if (parameter == "startPos" || parameter == "rate") displaySpectrogram()
    }

    override fun rescale() {
        super.rescale()
        displaySpectrogram()
    }

    private fun loadSpectrogram() {
        envelopesPane.children.removeAll(spectrogramViews)
        spectrogramViews.clear()
        val imageFile = obj.sample?.spectrogramFile ?: return
        if (!imageFile.isFile) return
        image = Image(imageFile.inputStream())
        displaySpectrogram()
    }

    private fun displaySpectrogram() {
        envelopesPane.children.removeAll(spectrogramViews)
        spectrogramViews.clear()
        val sample = obj.sample ?: return
        val startPos = obj.playbufStartPos ?: 0.0
        val rate = obj.playBufRate ?: 1.0
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
        val pixelsPerSecond = image.width / sampleDuration * rate
        val minX = pixelsPerSecond * startPos
        val minY = 0.0
        val width = pixelsPerSecond * duration
        val height = image.height
        view.viewport = Rectangle2D(minX, minY, width, height)
        view.fitHeight = prefHeight
        view.fitWidth = pane.getWidth(duration)
    }

    private fun setupSynthDefReference() {
        val nameLabel = label(obj.synthDef.name) styleClass "synth-def-ref-label"
        val viewBtn = Icon.View.button(action = "View SynthDef") {
            context[InstrumentRegistryPane].editSynthDef(obj.synthDef)
        }
        val box = HBox(nameLabel, viewBtn) styleClass "synth-def-ref-box"
        header.children.add(1, box)
    }

    private fun listenForMouseEvents() {
        addEventHandler(MouseEvent.MOUSE_CLICKED) { ev ->
            if (ev.clickCount >= 2 && obj.controls.isNotEmpty()) {
                openControlAssignment()
                ev.consume()
            }
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
                control !is EnvelopeControl || !control.display
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
                val control = EnvelopeControl(env, spec.associatedColor, display = true)
                obj.reassignControl(name, control)
            }
            menu.items.add(item)
        }
        menu.isAutoHide = true
        menu.show(scene.window, point.x, point.y)
    }

    fun openControlAssignment() {
        //TODO: Highlight unused controls in assignment view with ability to remove them
        cleanupControls()
        context[UndoManager].finishCompoundEdit()
        ControlAssignmentView.show(obj, context[currentProject])
    }

    private fun cleanupControls() {
        context[UndoManager].beginCompoundEdit("Adjust controls to changes in SynthDef")
        val parameters = obj.synthDef.parameters.now
        val parameterNames = parameters.map { p -> p.name.now }
        for ((parameter, _) in obj.controls.toMap()) {
            if (parameter !in parameterNames) obj.removeControl(parameter)
        }
        for (param in parameters) {
            val name = param.name.now
            if (name !in obj.controls.keys) {
                val control = param.defaultControl(context)
                obj.addControl(name, control)
            }
        }
    }

    override val defaultBackgroundColor: ReactiveValue<Color>
        get() = obj.synthDef.color
}