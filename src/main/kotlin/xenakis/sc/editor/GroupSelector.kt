package xenakis.sc.editor

import hextant.context.Context
import hextant.core.editor.SimpleChoiceEditor
import hextant.serial.SnapshotAware
import kotlinx.serialization.Serializable
import reaktive.value.now
import xenakis.model.GroupObject
import xenakis.sc.view.GroupSelectorControl
import xenakis.ui.XenakisController

@Suppress("SERIALIZER_TYPE_INCOMPATIBLE")
@Serializable(with = SnapshotAware.Serializer::class)
class GroupSelector(context: Context, group: GroupObject = GroupObject.DEFAULT) :
    SimpleChoiceEditor<GroupObject>(context, group) {
    override fun choices(): List<GroupObject> {
        val project = context[XenakisController.currentProject]
        return project.groups.asList() + GroupSelectorControl.createNew
    }

    override fun toString(choice: GroupObject): String = when {
        choice.name.now == "<create-new>" -> "Create new"
        else -> choice.name.now
    }
}