package xenakis.ui.score

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
import xenakis.model.score.ScoreObject
import xenakis.model.score.ScoreObjectInstance

class ScoreObjectSelectionManager(private val context: Context, private val rootPane: ScorePane) {
    private val _selectedViews = mutableSetOf<ScoreObjectView>()

    val selectedViews: Set<ScoreObjectView> get() = _selectedViews

    val selectedInstances: Set<ScoreObjectInstance> get() = selectedViews.mapTo(mutableSetOf()) { view -> view.instance }

    val selectedObjects: Set<ScoreObject> get() = selectedInstances.mapTo(mutableSetOf()) { inst -> inst.obj }

    private val _singleSelected = reactiveVariable<ScoreObjectView?>(null)
    val singleSelected: ReactiveValue<ScoreObjectView?> get() = _singleSelected

    private val focusedScorePane: ScorePane
        get() = (singleSelected.now as? ScoreObjectGroupView)?.scorePane
            ?: selectedViews.mapTo(mutableSetOf()) { v -> v.pane }.singleOrNull()
            ?: rootPane

    fun select(view: ScoreObjectView, addToSelection: Boolean): Boolean {
        for (v in selectedViews.toSet()) {
            if (!addToSelection || v.pane != view.pane) {
                _selectedViews.remove(v)
                updateIsSelected(v)
            }
        }
        if (view !in _selectedViews) _selectedViews.add(view)
        else _selectedViews.remove(view)
        _singleSelected.set(_selectedViews.singleOrNull())
        val selected = view in _selectedViews
        updateIsSelected(view)
        return selected
    }

    private fun updateIsSelected(view: ScoreObjectView) {
        val isSelected = view in selectedViews
        view.setSelected(isSelected)
        if (isSelected) {
            view.instance.obj.notifyListeners { isSomeInstanceSelected(true) }
        } else if (view.instance.obj !in selectedObjects) {
            view.instance.obj.notifyListeners { isSomeInstanceSelected(false) }
        }
    }

    fun removed(view: ScoreObjectView) {
        if (_selectedViews.remove(view)) {
            updateIsSelected(view)
        }
        if (view is ScoreObjectGroupView) {
            for (v in view.scorePane.allViews) {
                removed(v)
            }
        }
        _singleSelected.set(_selectedViews.singleOrNull())
    }

    fun deselectAll() {
        val previouslySelected = selectedViews.toSet()
        val previouslySelectedObjects = selectedObjects.toSet()
        _selectedViews.clear()
        _singleSelected.set(null)
        for (obj in previouslySelectedObjects) {
            obj.notifyListeners { isSomeInstanceSelected(false) }
        }
        for (v in previouslySelected) {
            v.setSelected(false)
        }
    }

    fun selectAll() {
        val focusedPane = focusedScorePane
        deselectAll()
        for (v in focusedPane.allViews) {
            _selectedViews.add(v)
            v.setSelected(true)
            v.instance.obj.notifyListeners { isSomeInstanceSelected(true) }
        }
        _singleSelected.set(_selectedViews.singleOrNull())
    }

    fun removeSelected() {
        val score = selectedInstances.firstOrNull()?.score ?: return
        score.removeObjects(selectedInstances)
        deselectAll()
    }

    fun setSystemClipboard(objects: List<ScoreObjectInstance>) {
        val json = Json.encodeToString(serializer(), objects)
        val clipboard = Clipboard.getSystemClipboard()
        clipboard.setContent(mapOf(ScoreObject.DATA_FORMAT to json))
    }

    fun getSystemClipboard(): List<ScoreObjectInstance>? {
        val clipboard = Clipboard.getSystemClipboard()
        if (!clipboard.hasContent(ScoreObject.DATA_FORMAT)) return null
        val content = clipboard.getContent(ScoreObject.DATA_FORMAT) as String
        val instances = Json.decodeFromString(serializer<List<ScoreObjectInstance>>(), content)
        return instances
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