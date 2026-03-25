package ponticello.sc.editor

import javafx.scene.input.DataFormat
import ponticello.model.code.GlobalPatternObject
import ponticello.model.code.GlobalPatternRegistry

class GlobalPatternSelector : ObjectSelector<GlobalPatternObject>() {
    override fun getOptions(): List<GlobalPatternObject> = context[GlobalPatternRegistry]

    override fun createNewObject(name: String): GlobalPatternObject = GlobalPatternObject.create(name)

    override fun dataFormat(): DataFormat = GlobalPatternObject.DATA_FORMAT
}