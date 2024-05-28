package xenakis.ui

import hextant.context.Context
import hextant.fx.registerShortcuts
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import xenakis.impl.UDPSuperColliderClient
import xenakis.model.GroupRegistry
import xenakis.sc.Group
import xenakis.sc.Identifier
import xenakis.sc.editor.IdentifierEditor
import xenakis.sc.view.IdentifierEditorControl

class GroupRegistryPane(
    private val context: Context,
    private val registry: GroupRegistry
) : VBox(), GroupRegistry.View {
    init {
        styleClass("tool-pane")
        children.add(createHeader())
        registry.addView(this)
    }

    private fun createHeader(): HBox {
        val label = Label("Groups").styleClass("tool-pane-heading")
        val space = infiniteSpace()
        val addBtn = Icon.Add.button(action = "Add group") { addNewGroup(context) }
        val reloadBtn = Icon.Repeat.button(action = "Reload SynthDefs") {
            val client = context[UDPSuperColliderClient]
            registry.setupGroups(client)
        }
        return HBox(label, space, addBtn, reloadBtn).styleClass("tool-pane-header")
    }

    override fun addedGroup(group: Group, index: Int) {
        val box = GroupBox(group)
        children.add(index + 1, box)
        if (index != 0) (children[index] as GroupBox).updateMoveButtons()
    }

    override fun removedGroup(group: Group, index: Int) {
        children.removeAt(index + 1)
    }

    override fun renamedGroup(group: Group, index: Int) {
        val box = children[index + 1] as GroupBox
        box.renamed()
    }

    override fun movedGroup(group: Group, fromIndex: Int, toIndex: Int) {
        val box = children.removeAt(fromIndex + 1)
        children.add(toIndex + 1, box)
    }

    private fun shiftFocus(from: Group, deltaIndex: Int) {
        val index = registry.indexOf(from) + deltaIndex
        if (deltaIndex in 1..registry.asList().size) {
            children[index].requestFocus()
        }
    }

    private fun removeGroup(group: Group) {
        if (!registry.hasReferences(group)) {
            registry.remove(group, null)
        } else {
            val title = "Replacement for group $group"
            showSelectorDialog(title, context, registry.asList() - group, Group.DEFAULT, Group::name) { replacement ->
                registry.remove(group, replacement)
            }
        }
    }

    private inner class GroupBox(private val group: Group) : HBox() {
        private val nameEditor = IdentifierEditor(context, group.name)
        private val nameControl = IdentifierEditorControl(nameEditor)
        private val obs = nameControl.onChangeCommited.observe { _, (_, newName) ->
            registry.renameGroup(group, newName)
        }
        val btnUp = Icon.Up.button(action = "Move up") { registry.moveGroup(group, deltaIndex = -1) }
        val btnDown = Icon.Down.button(action = "Move down") { registry.moveGroup(group, deltaIndex = +1) }
        val btnRemove = Icon.Delete.button(action = "Remove group") { removeGroup(group) }

        init {
            styleClass("group-box")
            children.addAll(nameControl, infiniteSpace(), btnUp, btnDown, btnRemove)
            registerShortcuts()
            updateMoveButtons()
        }

        private fun registerShortcuts() {
            registerShortcuts {
                on("UP") { shiftFocus(group, -1) }
                on("DOWN") { shiftFocus(group, +1) }
                on("Alt+UP") { registry.moveGroup(group, deltaIndex = -1) }
                on("Alt+DOWN") { registry.moveGroup(group, deltaIndex = -1) }
            }
        }

        fun renamed() {
            nameEditor.setText(group.name)
        }

        fun updateMoveButtons() {
            btnUp.isDisable = registry.indexOf(group) == 0
            btnDown.isDisable = registry.indexOf(group) == registry.asList().size - 1
        }
    }

    companion object {
        fun addNewGroup(context: Context, onAdded: (Group) -> Unit = {}) {
            val registry = context[GroupRegistry]
            showTextPrompt("Group name", "", context) { name ->
                if (Identifier.isValid(name)) {
                    val group = Group(name)
                    registry.add(group)
                    onAdded(group)
                    true
                } else false
            }
        }
    }
}