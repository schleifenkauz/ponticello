package xenakis.sc.editor

import xenakis.model.ctx.PonticelloContext
import xenakis.model.obj.ParameterizedObject
import xenakis.model.score.ParameterControlList
import xenakis.model.score.ParameterControlList.NamedParameterControl

class ParameterControlSelector : ObjectSelector<NamedParameterControl>() {
    private lateinit var associatedObject: ParameterizedObject

    override fun doInitialize() {
        associatedObject = context[PonticelloContext].associatedObject ?: error("No associated object found")
        super.doInitialize()
    }

    override fun getList(): ParameterControlList = associatedObject.controls

    override fun createNewObject(name: String): NamedParameterControl? = null //TODO
}