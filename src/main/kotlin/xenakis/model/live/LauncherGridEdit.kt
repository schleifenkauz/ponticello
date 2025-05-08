package xenakis.model.live

import fxutils.undo.AbstractEdit

abstract class LauncherGridEdit : AbstractEdit() {
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