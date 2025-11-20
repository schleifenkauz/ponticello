package ponticello.sc.editor

import javafx.scene.input.DataFormat
import ponticello.model.code.GlobalPatternObject
import ponticello.model.code.GlobalPatternRegistry
import ponticello.model.registry.ObjectRegistry

class GlobalPatternSelector : ObjectSelector<GlobalPatternObject>() {
    override fun getList(): ObjectRegistry<GlobalPatternObject> = context[GlobalPatternRegistry]

    override fun createNewObject(name: String): GlobalPatternObject = GlobalPatternObject.create(name)

    override fun dataFormat(): DataFormat = GlobalPatternObject.DATA_FORMAT
}