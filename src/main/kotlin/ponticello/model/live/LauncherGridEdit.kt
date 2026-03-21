package ponticello.model.live

import fxutils.undo.AbstractEdit
import ponticello.model.midi.LauncherGrid

abstract class LauncherGridEdit : AbstractEdit() {
    class SwapItems(
        val grid: LauncherGrid,
        val bankIndex: Int,
        val item1: GridItem,
        val item2: GridItem,
    ): LauncherGridEdit() {
        override val actionDescription: String
            get() = "Swap Launcher Grid items"

        override fun doRedo() {
            grid.swap(item1, item2, bankIndex)
        }

        override fun doUndo() {
            grid.swap(item2, item1, bankIndex)
        }
    }

    class AddBank(val grid: LauncherGrid, val bankIndex: Int) : LauncherGridEdit() {
        override val actionDescription: String
            get() = "Add pad bank"

        override fun doRedo() {
            grid.addBank()
        }

        override fun doUndo() {
            grid.removeBank(bankIndex)
        }
    }

    class RemoveBank(val grid: LauncherGrid, val bankIndex: Int) : LauncherGridEdit() {
        override val actionDescription: String
            get() = "Remove pad bank"

        override fun doRedo() {
            grid.removeBank(bankIndex)
        }

        override fun doUndo() {
            grid.addBank()
        }
    }
}