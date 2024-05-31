package xenakis.model

import kotlinx.serialization.Serializable
import reaktive.value.ReactiveVariable
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.SuperColliderClient
import xenakis.sc.editor.AbstractRenamableObject

@Serializable
data class GroupObject(
    override val mutableName: ReactiveVariable<String>,
    val isDefault: Boolean = false
) : AbstractRenamableObject() {
    val variableName: String get() = if (isDefault) "s.defaultGroup" else "~grp_${name.now}"

    override fun canRenameTo(newName: String): Boolean = !context[GroupRegistry].has(newName)

    override fun rename(newName: String) {
        val client = context[SuperColliderClient]
        val oldVariableName = variableName
        super.rename(newName)
        if (!isDefault) client.run("$variableName = ${oldVariableName}; $oldVariableName = nil;")
    }

    override fun createReference(): GroupObjectReference = GroupObjectReference(this)

    companion object {
        val DEFAULT = GroupObject(reactiveVariable("default"), isDefault = true)
    }
}