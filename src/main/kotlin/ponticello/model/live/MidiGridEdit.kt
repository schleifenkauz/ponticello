package ponticello.model.live

import fxutils.undo.AbstractEdit
import ponticello.model.midi.MidiGridInstrument

abstract class MidiGridEdit : AbstractEdit() {
    class SwapItems(
        val grid: MidiGridInstrument,
        val bankIndex: Int,
        val item1: GridItem,
        val item2: GridItem,
    ) : MidiGridEdit() {
        override val actionDescription: String
            get() = "Swap Launcher Grid items"

        override fun doRedo() {
            grid.swap(item1, item2, bankIndex)
        }

        override fun doUndo() {
            grid.swap(item2, item1, bankIndex)
        }
    }

    class AddBank(val grid: MidiGridInstrument, val bankIndex: Int) : MidiGridEdit() {
        override val actionDescription: String
            get() = "Add pad bank"

        override fun doRedo() {
            grid.addBank()
        }

        override fun doUndo() {
            grid.removeBank(bankIndex)
        }
    }

    class RemoveBank(val grid: MidiGridInstrument, val bankIndex: Int) : MidiGridEdit() {
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