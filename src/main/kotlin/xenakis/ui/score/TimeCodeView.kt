package xenakis.ui.score

import fxutils.centerChildren
import fxutils.styleClass
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import xenakis.impl.Decimal

class TimeCodeView : HBox() {
    private val minutes = Label() styleClass "time-code-label"
    private val seconds = Label() styleClass "time-code-label"
    private val hundreths = Label() styleClass "time-code-label"

    fun displayTime(time: Decimal) {
        val minutes = time.toInt() / 60
        val seconds = time.toInt() % 60
        val hundreths = ((time.value - time.value.toInt()) * 100).toInt()
        this.minutes.text = minutes.toString().padStart(2, '0')
        this.seconds.text = seconds.toString().padStart(2, '0')
        this.hundreths.text = hundreths.toString().padStart(2, '0')
    }

    init {
        styleClass("time-code", "toolbar-part")
        children.addAll(
            minutes, Label(":") styleClass "time-code-label",
            seconds, Label(":") styleClass "time-code-label",
            hundreths
        )
        centerChildren()
    }
}