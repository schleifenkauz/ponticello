package ponticello.model.live

import fxutils.centerChildren
import fxutils.controls.IntSpinner
import fxutils.prompt.ConfirmablePrompt
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import reaktive.value.reactiveVariable

class DjModeSetupPrompt : ConfirmablePrompt<Int>(
    "Setup track buses and mixer?",
    cancelText = "No", confirmText = "Yes"
) {
    private val nTracks = reactiveVariable(4)

    override val content: Node = HBox(
        Label("Number of tracks: "),
        IntSpinner(nTracks, 2..8)
    ).centerChildren()

    override fun confirm(): Int = nTracks.get()
}