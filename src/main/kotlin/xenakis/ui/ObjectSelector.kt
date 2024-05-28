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
import xenakis.model.LayoutManager
import xenakis.model.NamingManager
import xenakis.model.ScoreObject

class ObjectSelector(private val context: Context, private val rootPane: ScorePane) {
    private val _selectedViews = mutableSetOf<ScoreObjectView>()

    val selectedViews: Set<ScoreObjectView> get() = _selectedViews

    val selectedObjects get() = _selectedViews.map { view -> view.myObject }

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
        context[UndoManager].beginCompoundEdit()
        for (view in selectedViews) {
            view.pane.score.removeObject(view.myObject)
        }
        context[UndoManager].finishCompoundEdit("Remove objects")
    }

    fun copySelected() {
        if (selectedObjects.isEmpty()) return
        val copies = selectedObjects.map { obj ->
            val name = context[NamingManager].nameForCopy(obj)
            obj.copy(name)
        }
        setClipboard(copies)
    }

    fun cloneSelected() {
        if (selectedObjects.isEmpty()) return
        val clones = selectedObjects.map { obj ->
            val name = context[NamingManager].nameForClone(obj)
            obj.clone(name)
        }
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

    companion object : PublicProperty<ObjectSelector> by publicProperty("ObjectSelector")
}