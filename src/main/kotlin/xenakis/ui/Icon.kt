package xenakis.ui

import javafx.scene.control.Button
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.shape.Circle

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
    ExtraWindow;

    private val file = name.lowercase() + "_green.png"
    private val url = javaClass.getResource("icons/$file") ?: error("icon $file not found")
    private val image = Image(url.toExternalForm())

    fun getView(size: Double = 24.0): ImageView {
        val view = ImageView(image)
        view.isPreserveRatio = true
        view.fitWidth = size
        return view
    }

    fun button(radius: Double = 24.0, action: String? = null, onAction: (Button) -> Unit = {}): Button =
        Button().apply {
            graphic = getView(size = radius)
            shape = Circle(radius)
            tooltip = action?.let(::Tooltip)
            setMinSize(radius * 2, radius * 2)
            setMaxSize(radius * 2, radius * 2)
            setOnAction { onAction(this) }
            styleClass("icon-button")
        }

}