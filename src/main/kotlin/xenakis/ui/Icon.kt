package xenakis.ui

import javafx.scene.control.Button
import javafx.scene.control.ButtonBase
import javafx.scene.control.ToggleButton
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import xenakis.ui.impl.neverHGrow
import xenakis.ui.impl.styleClass

enum class Icon {
    Pointer, Resize,
    Synth, Code, Process,
    AddTime,
    Repeat,
    Delete,
    Settings,
    Details,
    AppIcon,
    Create,
    Play,
    Stop,
    Flow,
    Pause,
    Console,
    Save,
    Open,
    Restart,
    ExtraWindow,
    Search,
    Close,
    Edit,
    Check,
    Expand, Collapse,
    Add, Minus,
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
    SoundCode,
    Groups,
    Browser,
    Compound,
    Cut,
    Up, Down,
    Sync, AddGlobal, Log,
    Debug, Info, Confirmation, Warning, Error,
    RecordActive, RecordInactive,
    Duplicate, Oscilloscope;

    private val file = name.lowercase() + ".png"
    private val url = javaClass.getResource("icons/$file") ?: error("icon $file not found")
    val image: Image by lazy { Image(url.toExternalForm()) }

    fun getView(size: Double = DEFAULT_RADIUS * 1.25): ImageView {
        val view = ImageView(image)
        view.isPreserveRatio = true
        view.fitWidth = size
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