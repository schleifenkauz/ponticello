package ponticello.sc.editor

import ponticello.model.ctx.PonticelloContext
import ponticello.model.obj.ParameterizedObject
import ponticello.model.score.ParameterControlList
import ponticello.model.score.ParameterControlList.NamedParameterControl

class ParameterControlSelector : ObjectSelector<NamedParameterControl>() {
    private lateinit var associatedObject: ParameterizedObject

    override fun doInitialize() {
        associatedObject = context[PonticelloContext].associatedObject ?: error("No associated object found")
        super.doInitialize()
    }

    override fun getList(): ParameterControlList = associatedObject.controls

    override fun createNewObject(name: String): NamedParameterControl? = null
}