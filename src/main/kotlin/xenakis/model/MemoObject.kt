package xenakis.model

import hextant.core.editor.ViewManager
import xenakis.ui.MemoObjectView

class MemoObject(name: String, text: String, var width: Double) : ScoreObject(name) {
    override val viewManager = ViewManager.createWeakViewManager<MemoObjectView>()

    var text = text
        set(value) {
            if (value == field) return
            field = value
            viewManager.notifyViews { textChanged(value) }
        }

    override fun clone(): ScoreObject = MemoObject(name, text, width)
}