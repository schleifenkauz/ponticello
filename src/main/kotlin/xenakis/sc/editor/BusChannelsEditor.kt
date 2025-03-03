package xenakis.sc.editor

import hextant.context.Context
import hextant.core.EditorView
import hextant.core.editor.AbstractEditor
import reaktive.value.ReactiveValue
import reaktive.value.reactiveVariable

class BusChannelsEditor(context: Context, initial: Int = 2) : AbstractEditor<Int, BusChannelsEditor.View>(context) {
    private val numberOfChannels = reactiveVariable(initial)

    override val result: ReactiveValue<Int>
        get() = numberOfChannels

    fun selectNumberOfChannels(num: Int) {
        numberOfChannels.set(num)
    }

    interface View : EditorView {
        fun selected(num: Int)
    }
}