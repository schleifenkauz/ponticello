package xenakis.ui

import javafx.scene.control.Button
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.image.ImageView

enum class Icon {
    Envelope,
    Move,
    Synth,
    Code,
    Midi,
    Repeat,
    Delete,
    Settings,
    Details,
    Pointer,
    Create,
    Export,
    Play,
    Stop,
    Pause,
    AddTime,
    Console,
    Save,
    Open,
    Record,
    RecordInactive,
    RecordActive,
    Restart,
    Graph,
    ExtraWindow,
    Find,
    Close,
    Horizontal,
    Vertical,
    Edit,
    Check,
    Expand,
    Collapse,
    Add,
    View,
    Mute,
    Unmute,
    Undo, Redo,
    Memo,
    Color;

    private val file = name.lowercase() + "_green.png"
    private val url = javaClass.getResource("icons/$file") ?: error("icon $file not found")
    private val image = Image(url.toExternalForm(), 20.0, 20.0, true, false)

    fun getView(size: Double = 20.0): ImageView {
        val view = ImageView(image)
        view.isPreserveRatio = true
        view.fitWidth = size
        view.isSmooth = true
        return view
    }

    fun button(radius: Double = 16.0, action: String? = null, onAction: (Button) -> Unit = {}): Button =
        Button().apply {
            graphic = getView(size = radius * 1.25)
            /*shape = Circle(radius)*/
            tooltip = action?.let(::Tooltip)
            setMinSize(radius * 2, radius * 2)
            setMaxSize(radius * 2, radius * 2)
            setOnAction { onAction(this) }
            neverHGrow()
            styleClass("icon-button")
        }

}