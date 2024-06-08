package xenakis.model

import hextant.codegen.UseEditor
import hextant.context.Context
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.impl.ScWriter
import xenakis.sc.ScExpr
import xenakis.sc.editor.GroupSelector

@UseEditor(GroupSelector::class)
@Serializable(with = GroupObjectReference.Serializer::class)
class GroupObjectReference(name: String) : AbstractObjectReference<GroupObject>(name), ScExpr {
    constructor(obj: GroupObject) : this(obj.name.now) {
        this.obj = obj
    }

    override fun code(writer: ScWriter) {
        writer.append(get().variableName)
    }

    override fun getRegistry(context: Context): ObjectRegistry<GroupObject> = context[GroupRegistry]

    object Serializer : ObjectReference.Serializer<GroupObjectReference>() {
        override fun createReference(name: String): GroupObjectReference = GroupObjectReference(name)
    }
}