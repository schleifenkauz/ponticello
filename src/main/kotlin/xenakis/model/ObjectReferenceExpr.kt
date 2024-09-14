package xenakis.model

import hextant.context.Context
import kotlinx.serialization.Serializable
import xenakis.impl.ScWriter
import xenakis.sc.ScExpr

@Serializable
class ObjectReferenceExpr(val reference: ObjectReference?) : ScExpr {
    override fun code(writer: ScWriter, context: Context) {
        if (reference != null) writer.append(reference.get<SuperColliderObject>().superColliderName)
        else writer.append("<null>")
    }
}