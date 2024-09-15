package xenakis.ui

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import hextant.undo.UndoManager
import javafx.scene.input.Clipboard
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.ScoreObject
import xenakis.model.ScoreObjectInstance

class ScoreObjectSelectionManager(private val context: Context, private val rootPane: ScorePane) {
    private val _selectedViews = mutableSetOf<ScoreObjectView>()

    val selectedViews: Set<ScoreObjectView> get() = _selectedViews

    val selectedInstances: Set<ScoreObjectInstance> get() = _selectedViews.mapTo(mutableSetOf()) { view -> view.instance }

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
        val score = selectedInstances.firstOrNull()?.score ?: return
        score.removeObjects(selectedInstances)
    }

    fun cloneToClipboard() {
        if (selectedInstances.isEmpty()) return
        val copies = selectedInstances.map { inst ->
            inst.clone(inst.obj.name.now, inst.start, inst.y)
        }
        setClipboard(copies)
    }

    fun duplicateToClipboard() {
        if (selectedInstances.isEmpty()) return
        val clones = selectedInstances.map { inst -> inst.duplicate(inst.position) }
        setClipboard(clones)
    }

    private fun setClipboard(objects: List<ScoreObjectInstance>) {
        val json = Json.encodeToString(serializer(), objects)
        val clipboard = Clipboard.getSystemClipboard()
        clipboard.setContent(mapOf(ScoreObject.DATA_FORMAT to json))
    }

    fun toggleMuteSelected() {
        context[UndoManager].beginCompoundEdit("Toggle mute")
        for (obj in selectedInstances) {
            obj.toggleMuted()
        }
        context[UndoManager].finishCompoundEdit()
    }

    companion object : PublicProperty<ScoreObjectSelectionManager> by publicProperty("ObjectSelector")
}