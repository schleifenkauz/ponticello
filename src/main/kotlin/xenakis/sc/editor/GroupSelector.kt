package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.SimpleChoiceEditor
import hextant.serial.SnapshotAware
import kotlinx.serialization.Serializable
import xenakis.sc.Group
import xenakis.sc.view.GroupSelectorControl
import xenakis.ui.XenakisController

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = SnapshotAware.Serializer::class)
class GroupSelector(context: Context, group: Group = Group.DEFAULT) : SimpleChoiceEditor<Group>(context, group) {
    override fun choices(): List<Group> {
        val project = context[XenakisController.currentProject]
        return project.groups.asList() + GroupSelectorControl.createNew
    }

    override fun toString(choice: Group): String = when {
        choice.name == "<create-new>" -> "Create new"
        else -> choice.name
    }
}