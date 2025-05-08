package xenakis.ui.actions

import fxutils.actions.Action
import fxutils.undo.UndoManager
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import org.kordamp.ikonli.materialdesign2.MaterialDesignU

object UndoRedoActions: Action.Collector<UndoManager>({
    addAction("Undo") {
        shortcut("Ctrl+Z")
        description(UndoManager::undoText)
        icon(MaterialDesignU.UNDO)
        enableWhen { manager -> manager.canUndo }
        executes { manager -> manager.undo() }
    }
    addAction("Undo") {
        shortcut("Ctrl+Shift+Z")
        description(UndoManager::redoText)
        icon(MaterialDesignR.REDO)
        enableWhen { manager -> manager.canRedo }
        executes { manager -> manager.redo() }
    }
})