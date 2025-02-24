package xenakis.ui.actions

import hextant.undo.UndoManager
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import org.kordamp.ikonli.materialdesign2.MaterialDesignU

object UndoRedoActions: Action.Collector<UndoManager>({
    addAction("Undo") {
        shortcut("Ctrl+Z")
        description(UndoManager::undoText)
        ifNotApplicable(Action.IfNotApplicable.Disable)
        icon(MaterialDesignU.UNDO)
        applicableIf { manager -> manager.canUndo }
        executes { manager -> manager.undo() }
    }
    addAction("Undo") {
        shortcut("Ctrl+Shift+Z")
        description(UndoManager::redoText)
        ifNotApplicable(Action.IfNotApplicable.Disable)
        icon(MaterialDesignR.REDO)
        applicableIf { manager -> manager.canRedo }
        executes { manager -> manager.redo() }
    }
})