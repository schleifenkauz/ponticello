package xenakis.ui

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import hextant.undo.UndoManager
import javafx.scene.input.Clipboard
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.ClonedObject
import xenakis.model.LayoutManager
import xenakis.model.ScoreObject

class ScoreObjectSelector(private val context: Context, private val rootPane: ScorePane) {
    private val _selectedViews = mutableSetOf<ScoreObjectView>()

    val selectedViews: Set<ScoreObjectView> get() = _selectedViews

    val selectedObjects: Set<ScoreObject> get() = _selectedViews.mapTo(mutableSetOf()) { view -> view.myObject }

    private val _singleSelected = reactiveVariable<ScoreObjectView?>(null)
    val singleSelected: ReactiveValue<ScoreObjectView?> get() = _singleSelected

    val focusedScorePane: ScorePane get() = singleSelected.now?.pane ?: rootPane

    fun select(view: ScoreObjectView, addToSelection: Boolean): Boolean {
        for (selected in selectedViews.toSet()) {
            if (selected.pane != view.pane || !addToSelection) {
                _selectedViews.remove(selected)
                selected.setSelected(false)
            }
        }
        if (view !in _selectedViews) _selectedViews.add(view)
        else _selectedViews.remove(view)
        _singleSelected.set(_selectedViews.singleOrNull())
        val selected = view in _selectedViews
        view.setSelected(selected)
        return selected
    }

    fun deselectAll() {
        for (v in _selectedViews) v.setSelected(false)
        _selectedViews.clear()
        _singleSelected.set(null)
    }

    fun selectAll() {
        deselectAll()
        for (v in focusedScorePane.allViews) select(v, addToSelection = true)
    }

    fun removeSelected() {
        val score = selectedObjects.firstOrNull()?.parent ?: return
        if (selectedObjects.any { obj -> rootPane.score.hasClonesOf(obj) }) {
            val removeNevertheless =
                showYesNoDialog("Some of the selected objects have clones. Those will be removed too. Continue?")
            if (removeNevertheless != true) return
        }
        context[UndoManager].beginCompoundEdit()
        score.removeObjects(selectedObjects)
        context[UndoManager].finishCompoundEdit("Remove objects")
    }

    fun copySelected() {
        if (selectedObjects.isEmpty()) return
        val copies = selectedObjects.map { obj ->
            if (obj is ClonedObject) {
                val copy = obj.original.copy(obj.original.name.now)
                copy.position.set(obj.position)
                copy
            } else obj
        }
        setClipboard(copies)
    }

    fun cloneSelected() {
        if (selectedObjects.isEmpty()) return
        val clones = selectedObjects.map { obj -> obj.clone() }
        setClipboard(clones)
    }

    private fun setClipboard(objects: List<ScoreObject>) {
        val json = Json.encodeToString(ListSerializer(ScoreObject.Ser), objects)
        val clipboard = Clipboard.getSystemClipboard()
        clipboard.setContent(mapOf(ScoreObject.DATA_FORMAT to json))
    }

    fun toggleMuteSelected() {
        context[UndoManager].beginCompoundEdit("Toggle mute")
        for (obj in selectedObjects) {
            obj.muted = !obj.muted
        }
        context[UndoManager].finishCompoundEdit()
    }

    fun addLayoutGroup(aspect: LayoutManager.LayoutAspect) {
        val layoutManager = focusedScorePane.score.layoutManager
        layoutManager.addGroup(aspect, selectedObjects)
    }

    fun removeFromLayoutGroup(aspect: LayoutManager.LayoutAspect) {
        for (obj in selectedObjects) {
            val layoutManager = obj.parent!!.layoutManager
            layoutManager.removeFromGroup(obj, aspect)
        }
    }

    companion object : PublicProperty<ScoreObjectSelector> by publicProperty("ObjectSelector")
}