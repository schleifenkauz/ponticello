package xenakis.ui

import javafx.scene.control.Button
import javafx.scene.control.ButtonBase
import javafx.scene.control.ToggleButton
import javafx.scene.control.Tooltip
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.javafx.FontIcon
import org.kordamp.ikonli.materialdesign2.*
import xenakis.ui.impl.neverHGrow
import xenakis.ui.impl.styleClass

//CHART_TIMELINE_VARIANT
//CHART_TIMELINE
//FILE_COG
//UNGROUP

enum class Icon(val iconCode: Ikon) {
    Pointer(MaterialDesignC.CURSOR_DEFAULT), Resize(MaterialDesignA.ARROW_EXPAND),
    Synth(MaterialDesignS.SINE_WAVE), Code(MaterialDesignC.CODE_BRACES), Process(MaterialDesignP.PROGRESS_QUESTION),
    AddTime(MaterialDesignP.PROGRESS_QUESTION),
    Repeat(MaterialDesignR.REPEAT),
    Delete(MaterialDesignD.DELETE),
    Settings(MaterialDesignC.COG),
    Details(MaterialDesignT.TUNE),
    AppIcon(MaterialDesignP.PROGRESS_QUESTION),
    Create(MaterialDesignF.FILE_PLUS),
    Play(MaterialDesignP.PLAY),
    Stop(MaterialDesignS.STOP),
    Flow(MaterialDesignG.GRAPH),
    Pause(MaterialDesignP.PAUSE),
    Console(MaterialDesignC.CONSOLE),
    Save(MaterialDesignP.PROGRESS_QUESTION),
    Open(MaterialDesignP.PROGRESS_QUESTION),
    Restart(MaterialDesignR.RESTART),
    ExtraWindow(MaterialIcon.OPEN_IN_NEW),
    Close(MaterialDesignC.CLOSE),
    Search(MaterialDesignP.PROGRESS_QUESTION),
    Edit(MaterialDesignS.SQUARE_EDIT_OUTLINE),
    Check(MaterialDesignC.CHECK_CIRCLE),
    Expand(MaterialDesignC.CHEVRON_DOWN), Collapse(MaterialDesignC.CHEVRON_UP),
    Add(MaterialDesignP.PLUS), Minus(MaterialDesignM.MINUS),
    View(MaterialDesignE.EYE), //maybe CODE_TAGS for SynthDefs
    Mute(MaterialDesignV.VOLUME_VARIANT_OFF),
    Unmute(MaterialDesignV.VOLUME_HIGH),
    Undo(MaterialDesignU.UNDO), Redo(MaterialDesignR.REDO),
    Memo(MaterialDesignM.MESSAGE_REPLY_TEXT),
    Knob(MaterialDesignP.PROGRESS_QUESTION),
    Midi(MaterialDesignP.PIANO),
    Tempo(MaterialDesignM.METRONOME),
    Snap(MaterialDesignM.MAGNET), //MAGNET_ON
    TimeGrid(MaterialDesignP.PROGRESS_QUESTION),
    Transpose(MaterialDesignP.PROGRESS_QUESTION),
    GoToStart(MaterialDesignS.SKIP_PREVIOUS),
    SetupCode(MaterialDesignP.PROGRESS_QUESTION),
    Reverse(MaterialDesignP.PROGRESS_QUESTION),
    Grab(MaterialDesignC.CURSOR_POINTER),
    Bus(MaterialDesignP.PROGRESS_QUESTION),
    Samples(MaterialDesignP.PROGRESS_QUESTION),
    Groups(MaterialDesignP.PROGRESS_QUESTION),
    Browser(MaterialDesignW.WEB),
    Compound(MaterialDesignG.GROUP),
    Cut(MaterialDesignS.SCISSORS_CUTTING),
    Up(MaterialDesignA.ARROW_UP), Down(MaterialDesignA.ARROW_DOWN),
    Sync(MaterialDesignS.SYNC), AddGlobal(MaterialDesignP.PROGRESS_QUESTION), Log(MaterialDesignB.BELL),
    Debug(MaterialDesignB.BUG), Info(MaterialDesignI.INFORMATION), Confirmation(MaterialDesignC.CHECK_CIRCLE),
    Warning(MaterialDesignA.ALERT), Error(MaterialDesignA.ALERT_OCTAGON),
    RecordActive(MaterialDesignM.MICROPHONE), RecordInactive(MaterialDesignM.MICROPHONE_OUTLINE),
    Duplicate(MaterialDesignC.CONTENT_DUPLICATE), Options(MaterialDesignD.DOTS_HORIZONTAL);

    private val file = name.lowercase() + ".png"
    private val url = javaClass.getResource("icons/$file") ?: error("icon $file not found")
    val image: Image by lazy { Image(url.toExternalForm()) }

    fun getView(size: Double = DEFAULT_RADIUS * 1.25): ImageView {
        val view = ImageView(image)
        view.isPreserveRatio = true
        view.fitWidth = size
        return view
    }

    fun getGraphic() = FontIcon(iconCode)

    fun button(radius: Double = DEFAULT_RADIUS, action: String? = null, onAction: (MouseEvent) -> Unit = {}): Button =
        Button().apply {
            configureButton(radius, action)
            setOnMouseClicked { ev -> onAction(ev) }
        }

    private fun ButtonBase.configureButton(radius: Double, description: String?) {
        graphic = FontIcon(iconCode)
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