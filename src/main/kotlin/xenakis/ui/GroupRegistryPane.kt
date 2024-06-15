package xenakis.ui

import hextant.fx.registerShortcuts
import javafx.scene.control.Button
import reaktive.value.reactiveVariable
import xenakis.model.GroupObject
import xenakis.model.GroupRegistry

class GroupRegistryPane(
    private val registry: GroupRegistry
) : SuperColliderObjectRegistryPane<GroupObject>(registry), GroupRegistry.View {
    init {
        registry.addView(this)
    }

    override fun added(obj: GroupObject, idx: Int) {
        super.added(obj, idx)
        box(idx).updateMoveButtons()
        updateMoveButtonsAround(idx)
    }

    override fun removed(obj: GroupObject, idx: Int) {
        super.removed(obj, idx)
        updateMoveButtonsAround(idx)
    }

    override fun canDelete(obj: GroupObject): Boolean = !obj.isDefault

    private fun updateMoveButtonsAround(idx: Int) {
        val indices = boxes.children.indices
        if (idx - 1 in indices) box(idx - 1).updateMoveButtons()
        if (idx + 1 in indices) box(idx + 1).updateMoveButtons()
    }

    override fun ObjectBox<GroupObject>.configureObjectBox() {
        addAction(Icon.Down, "Move down") { registry.moveGroup(obj, deltaIndex = +1) }
        addAction(Icon.Up, "Move up") { registry.moveGroup(obj, deltaIndex = -1) }
        registerShortcuts {
            on("UP") { shiftFocus(obj, -1) }
            on("DOWN") { shiftFocus(obj, +1) }
            on("Alt+UP") { registry.moveGroup(obj, deltaIndex = -1) }
            on("Alt+DOWN") { registry.moveGroup(obj, deltaIndex = -1) }
        }
    }

    private fun ObjectBox<GroupObject>.updateMoveButtons() {
        val btnUp = actions.children[0] as Button
        val btnDown = actions.children[1] as Button
        btnUp.isDisable = registry.indexOf(obj) == 0
        btnDown.isDisable = registry.indexOf(obj) == registry.asList().size - 1
    }

    override fun movedGroup(group: GroupObject, fromIndex: Int, toIndex: Int) {
        val box = boxes.children.removeAt(fromIndex)
        boxes.children.add(toIndex, box)
        updateMoveButtonsAround(fromIndex)
        updateMoveButtonsAround(toIndex)
    }

    override fun addObject(name: String): GroupObject {
        val obj = GroupObject(reactiveVariable(name))
        registry.add(obj)
        return obj
    }

    private fun shiftFocus(from: GroupObject, deltaIndex: Int) {
        val index = registry.indexOf(from) + deltaIndex
        if (deltaIndex in boxes.children.indices) {
            boxes.children[index].requestFocus()
        }
    }
}