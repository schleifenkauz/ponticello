package ponticello.ui.score

import fxutils.controls.AbstractSpinner
import fxutils.controls.IntSpinner
import fxutils.hspace
import fxutils.prompt.DetailPane
import fxutils.styleClass
import fxutils.undo.UndoManager
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.scene.canvas.Canvas
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.Background
import javafx.scene.paint.Color
import javafx.scene.shape.Line
import ponticello.impl.Decimal
import ponticello.impl.one
import ponticello.impl.times
import ponticello.impl.toDecimal
import ponticello.model.obj.project
import ponticello.model.project.InlineControlsDisplay
import ponticello.model.project.uiState
import ponticello.model.score.MeterObject
import ponticello.model.score.ScoreObjectInstance
import ponticello.model.score.TempoGridObject
import ponticello.model.score.TimeUnit
import ponticello.ui.impl.DecimalSpinner
import ponticello.ui.midi.MidiContext
import ponticello.ui.score.TempoGrid.Companion.GRID_HEIGHT
import reaktive.Observer
import reaktive.and
import reaktive.value.ReactiveVariable
import reaktive.value.fx.asObservableValue
import reaktive.value.now

class TempoGridObjectView(override val obj: TempoGridObject, inst: ScoreObjectInstance) : ScoreObjectView(inst) {
    private val canvas = Canvas()
    private val marker = Line() styleClass "grid-marker-line"
    private lateinit var snapObserver: Observer
    override val tempoGrid: TempoGrid?
        get() {
            val meter = obj.meter.get() ?: return null
            val barOffset = meter.getDuration(TimeUnit.Bars) * obj.firstBar.now
            val scale = one
            return TempoGrid(
                TempoGrid.GridType.Regular, obj,
                instance::position,
                meter, scale, barOffset,
                canvas, marker
            )
        }

    init {
        styleClass("tempo-grid")
        children.addAll(canvas, marker)
        canvas.height = GRID_HEIGHT
        canvas.widthProperty().bind(Bindings.min(MAX_OBJECT_WIDTH, prefWidthProperty()))
        marker.endYProperty().bind(heightProperty())
    }

    override val borderColorWhenSelected: Color
        get() = Color.GREEN

    override fun initialize() {
        super.initialize()
        repaint()
        val settings = context.project.uiState
        snapObserver = settings.snapOption.observe { _ -> repaint() }
            .and(settings.snapEnabled.observe { _ -> repaint() })
    }

    override fun configureInlineControls() {
        inlineControls.children.add(0, hspace(20.0)) //to avoid collision of name label with bar number
    }

    override fun setupDetailPane(pane: DetailPane, midiContext: MidiContext?) {
        val meter = obj.meter.get()
        if (meter == null) {
            pane.children.add(Label("Unresolved meter ${obj.meter.getName()}").styleClass("-fx-text-fill: red;"))
            return
        }
        setupMeterConfig(meter, pane, obj.context[UndoManager])

        val firstBarSpinner = IntSpinner(obj.firstBar, 0..1000)
            .setupUndo("First bar", obj.context[UndoManager])
            .minColumns(3)
        val nameButton = Button()
        nameButton.textProperty().bind(obj.meter.name.asObservableValue())
        pane.addItem("Meter", nameButton)
        pane.addItem("First bar:", firstBarSpinner)
    }

    override fun rescale() {
        super.rescale()
        repaint()
    }

    fun updatedConfig() {
        repaint()
    }

    override fun getDisplayHeight(): Double = GRID_HEIGHT

    private fun repaint() {
        val grid = tempoGrid
        if (grid == null) {
            marker.visibleProperty().unbind()
            marker.isVisible = false
            return
        }
        val offset = if (prefWidth > MAX_OBJECT_WIDTH && layoutX < 0.0) -layoutX else 0.0
        canvas.translateX = offset
        val offsetDur = getDuration(offset)
        grid.paintGrid(parentPane.pixelsPerSecond, offsetDur)
    }

    override fun relocate(x: Double, y: Double) {
        val layoutXBefore = layoutX
        super.relocate(x, y)
        if (prefWidth > MAX_OBJECT_WIDTH && (layoutXBefore < 0.0 || x < 0.0)) {
            repaint()
        }
    }

    override fun inlineControlsBackground(
        controlsDisplay: ReactiveVariable<InlineControlsDisplay>,
    ): ObservableValue<Background> = SimpleObjectProperty(Background.EMPTY)

    companion object {
        fun setupMeterConfig(
            meter: MeterObject, pane: DetailPane, undoManager: UndoManager?,
        ): AbstractSpinner<Decimal> {
            val bpmSpinner = DecimalSpinner(
                meter.beatsPerMinute,
                min = 10.0.toDecimal(), max = 500.0.toDecimal(),
                step = one, maxPrecision = 2
            ).setupUndo("BPM", undoManager)
                .minColumns(3)
            val bpbSpinner = IntSpinner(meter.beatsPerBar, 1..24)
                .setupUndo("Beats per bar", undoManager)
                .minColumns(3)
            val tpbSpinner = IntSpinner(meter.ticksPerBeat, 1..24)
                .setupUndo("Ticks per beat", undoManager)
                .minColumns(3)
            pane.addItem("BPM:", bpmSpinner)
            pane.addItem("Beats", bpbSpinner)
            pane.addItem("Ticks", tpbSpinner)
            return bpmSpinner
        }
    }
}