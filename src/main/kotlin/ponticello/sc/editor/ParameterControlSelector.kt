package ponticello.sc.editor

import bundles.getOrNull
import ponticello.impl.Logger
import ponticello.model.ctx.PonticelloContext
import ponticello.model.instr.ParameterizedObject
import ponticello.model.score.controls.ParameterControlList
import ponticello.model.score.controls.ParameterControlList.NamedParameterControl

class ParameterControlSelector : ObjectSelector<NamedParameterControl>() {
    private lateinit var associatedObject: ParameterizedObject

    override fun doInitialize() {
        val ctx = context.getOrNull(PonticelloContext)
        if (ctx is PonticelloContext.Control) {
            associatedObject = ctx.control.parentObject
        } else {
            Logger.error("PonticelloContext of $this is $ctx but should be a PonticelloContext.Control")
        }
        super.doInitialize()
    }

    override fun getList(): ParameterControlList = associatedObject.controls

    override fun createNewObject(name: String): NamedParameterControl? = null
}