package xenakis.ui

import javafx.scene.control.Button
import javafx.scene.control.ButtonBase
import javafx.scene.control.ToggleButton
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent

enum class Icon {
    Envelope,
    Synth,
    Code,
    Repeat,
    Delete,
    Settings,
    Details,
    AppIcon,
    Pointer,
    Create,
    Play,
    Stop,
    Pause,
    AddTime,
    Console,
    Save,
    Open,
    Restart,
    Graph,
    ExtraWindow,
    Find,
    Close,
    Horizontal, HorizontalRemove,
    Vertical, VerticalRemove,
    Edit,
    Check,
    Expand,
    Add,
    View,
    Mute,
    Unmute,
    Undo, Redo,
    Memo,
    Knob,
    Midi,
    Tempo,
    Snap,
    TimeGrid,
    Transpose,
    GoToStart,
    SetupCode,
    Reverse,
    Grab,
    Bus,
    Samples,
    Instrument,

    Browser,
    Compound,
    Cut,
    Up, Down,
    Sync, AddGlobal, Search;

    private val file = name.lowercase() + "_green.png"
    private val url = javaClass.getResource("icons/$file") ?: error("icon $file not found")
    val image: Image = Image(url.toExternalForm())

    fun getView(size: Double = DEFAULT_RADIUS * 1.25): ImageView {
        val view = ImageView(image)
        view.isPreserveRatio = true
        view.fitWidth = size
        view.isSmooth = false
        return view
    }

    fun button(radius: Double = DEFAULT_RADIUS, action: String? = null, onAction: (MouseEvent) -> Unit = {}): Button =
        Button().apply {
            configureButton(radius, action)
            setOnMouseClicked { ev -> onAction(ev) }
        }

    private fun ButtonBase.configureButton(radius: Double, description: String?) {
        graphic = getView(size = radius * 1.25)
        tooltip = description?.let(::Tooltip)
        setMinSize(radius * 2, radius * 2)
        setMaxSize(radius * 2, radius * 2)
        neverHGrow()
        styleClass("icon-button")
    }

    fun toggleButton(radius: Double = DEFAULT_RADIUS, description: String? = null): ToggleButton =
        ToggleButton().apply {
            configureButton(radius, description)
            styleClass("icon-toggle")
        }

    companion object {
        const val DEFAULT_RADIUS: Double = 16.0
    }
}