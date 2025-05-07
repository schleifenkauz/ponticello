package xenakis.model.live

import hextant.undo.AbstractEdit

abstract class LauncherGridEdit : AbstractEdit() {
    class SetItemTarget(
        val item: LauncherGrid.GridItem,
        val oldTarget: LauncherGrid.ItemTarget,
        val newTarget: LauncherGrid.ItemTarget,
    ) : LauncherGridEdit() {
        override val actionDescription: String
            get() = "Set Launcher Grid item target"

        override fun doRedo() {
            item.target = newTarget
        }

        override fun doUndo() {
            item.target = oldTarget
        }
    }

    class SwapItems(
        val grid: LauncherGrid,
        val item1: LauncherGrid.GridItem,
        val item2: LauncherGrid.GridItem,
    ): LauncherGridEdit() {
        override val actionDescription: String
            get() = "Swap Launcher Grid items"

        override fun doRedo() {
            grid.swap(item1, item2)
        }

        override fun doUndo() {
            grid.swap(item2, item1)
        }
    }
}