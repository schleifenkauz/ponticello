package xenakis.model

import hextant.context.Context
import kotlinx.serialization.Serializable
import xenakis.model.obj.SuperColliderObject
import xenakis.model.registry.ObjectReference
import xenakis.sc.ScExpr
import xenakis.sc.client.ScWriter

@Serializable
class ObjectReferenceExpr(val reference: ObjectReference?) : ScExpr {
    override fun code(writer: ScWriter, context: Context) {
        if (reference != null) writer.append(reference.get<SuperColliderObject>().superColliderName)
        else writer.append("<null>")
    }
}