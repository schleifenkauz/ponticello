package ponticello.ui.score

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import javafx.scene.input.Clipboard
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import ponticello.model.score.Score
import ponticello.model.score.ScoreObject
import ponticello.model.score.ScoreObjectInstance
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable

class ScoreObjectSelectionManager(val context: Context, private val rootPane: ScorePane) {
    private val _selectedViews = mutableSetOf<ScoreObjectView>()

    val selectedViews: Set<ScoreObjectView> get() = _selectedViews

    val selectedInstances: Set<ScoreObjectInstance> get() = selectedViews.mapTo(mutableSetOf()) { view -> view.instance }

    val selectedObjects: Set<ScoreObject> get() = selectedInstances.mapTo(mutableSetOf()) { inst -> inst.obj }

    private val _focusedView = reactiveVariable<ScoreObjectView?>(null)

    val focusedView: ReactiveValue<ScoreObjectView?> get() = _focusedView

    val focusedInstance get() = focusedView.now?.instance

    val focusedObject get() = focusedInstance?.obj

    val focusedScorePane: ScorePane
        get() = (focusedView.now as? ScoreObjectGroupView)?.scorePane
            ?: selectedViews.mapTo(mutableSetOf()) { v -> v.parentPane }.singleOrNull()
            ?: rootPane

    fun isSelected(view: ScoreObjectView): Boolean = view in selectedViews

    fun isSelected(instance: ScoreObjectInstance): Boolean = instance in selectedInstances

    fun isSelected(obj: ScoreObject): Boolean = obj in selectedObjects

    fun isFocused(view: ScoreObjectView): Boolean = _focusedView.now == view

    fun selectAll(views: Collection<ScoreObjectView>, addToSelection: Boolean) {
        val selectedBefore = selectedViews.toSet()
        if (!addToSelection) {
            _selectedViews.clear()
            _selectedViews.addAll(views)
            for (v in selectedBefore - views.toSet()) {
                updateIsSelected(v, false)
            }
            for (v in views - selectedBefore) {
                updateIsSelected(v, true)
            }
        } else {
            _selectedViews.addAll(views)
            for (v in views - selectedBefore) {
                updateIsSelected(v, true)
            }
        }
    }

    fun select(view: ScoreObjectView, addToSelection: Boolean) {
        if (!addToSelection) {
            val selectedBefore = selectedViews.toSet()
            _selectedViews.clear()
            _selectedViews.add(view)
            setFocused(view)
            for (v in selectedBefore - view) {
                updateIsSelected(v, false)
            }
        } else {
            if (view !in _selectedViews) _selectedViews.add(view)
            else _selectedViews.remove(view)
            if (view in _selectedViews) setFocused(view)
            else updateIsSelected(view, false)
        }
    }

    private fun setFocused(view: ScoreObjectView?) {
        val previouslyFocused = _focusedView.now
        if (previouslyFocused == view) return
        _focusedView.set(view)
        previouslyFocused?.updateIsFocused(false)
        if (view != null) updateIsSelected(view, true)
    }

    private fun updateIsSelected(view: ScoreObjectView, isSelected: Boolean) {
        if (isSelected) {
            view.obj.notifyListeners { updateIsSomeInstanceSelected(true) }
            view.updateIsFocused(true)
        } else {
            if (focusedView.now == view) {
                setFocused(null)
            } else {
                view.updateIsSelected(false)
            }
            if (view.obj !in selectedObjects) {
                view.obj.notifyListeners { updateIsSomeInstanceSelected(false) }
            }
        }
    }

    fun removed(view: ScoreObjectView) {
        if (_selectedViews.remove(view)) {
            updateIsSelected(view, false)
        }
        if (view is ScoreObjectGroupView) {
            for (v in view.scorePane.allViews) {
                removed(v)
            }
        }
        _focusedView.set(_selectedViews.singleOrNull())
    }

    fun deselectAll() {
        val previouslySelected = selectedViews.toSet()
        val previouslySelectedObjects = selectedObjects.toSet()
        _selectedViews.clear()
        setFocused(null)
        for (obj in previouslySelectedObjects) {
            obj.notifyListeners { updateIsSomeInstanceSelected(false) }
        }
        for (v in previouslySelected) {
            v.updateIsSelected(false)
        }
        RectangleSelection.clear()
    }

    fun selectAll() {
        val focusedPane = focusedScorePane
        deselectAll()
        for (v in focusedPane.allViews) {
            _selectedViews.add(v)
            v.updateIsSelected(true)
            v.obj.notifyListeners { updateIsSomeInstanceSelected(true) }
        }
        setFocused(_selectedViews.singleOrNull())
    }

    fun removeSelected() {
        for (inst in selectedInstances) {
            val score = inst.score ?: error("No score associated with $inst")
            score.removeObject(inst, Score.RegistryOption.ASK_IF_NEEDED)
        }
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

    companion object : PublicProperty<ScoreObjectSelectionManager> by publicProperty("ObjectSelector")
}