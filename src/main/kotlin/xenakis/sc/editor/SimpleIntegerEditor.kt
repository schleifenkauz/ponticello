package xenakis.sc.editor

import hextant.core.editor.SimpleEditor
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

//@Serializable TODO why does serializer fail
class SimpleIntegerEditor() : SimpleEditor<Int>() {
    constructor(value: Int): this() {
        setInitialResult(value)
    }
}