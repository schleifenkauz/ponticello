package xenakis.ui.score

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import javafx.scene.input.Clipboard
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.model.score.Score
import xenakis.model.score.ScoreObject
import xenakis.model.score.ScoreObjectInstance
import xenakis.ui.launcher.DetailPaneManager

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

    fun select(view: ScoreObjectView, addToSelection: Boolean): Boolean {
        if (!addToSelection) {
            val selectedBefore = selectedViews.toSet()
            _selectedViews.clear()
            for (v in selectedBefore) {
                updateIsSelected(v)
            }
        }
        if (view !in _selectedViews) _selectedViews.add(view)
        else _selectedViews.remove(view)
        _focusedView.set(_selectedViews.singleOrNull())
        val selected = view in _selectedViews
        updateIsSelected(view)
        return selected
    }

    private fun updateIsSelected(view: ScoreObjectView) {
        val isSelected = view in selectedViews
        view.setSelected(isSelected)
        if (isSelected) {
            view.obj.notifyListeners { isSomeInstanceSelected(true) }
        } else if (view.obj !in selectedObjects) {
            view.obj.notifyListeners { isSomeInstanceSelected(false) }
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
        _focusedView.set(_selectedViews.singleOrNull())
    }

    fun deselectAll() {
        context[DetailPaneManager].hideCurrentlyShown()
        val previouslySelected = selectedViews.toSet()
        val previouslySelectedObjects = selectedObjects.toSet()
        _selectedViews.clear()
        _focusedView.set(null)
        for (obj in previouslySelectedObjects) {
            obj.notifyListeners { isSomeInstanceSelected(false) }
        }
        for (v in previouslySelected) {
            v.setSelected(false)
        }
        focusedScorePane.clearRegionSelection()
    }

    fun selectAll() {
        val focusedPane = focusedScorePane
        deselectAll()
        for (v in focusedPane.allViews) {
            _selectedViews.add(v)
            v.setSelected(true)
            v.obj.notifyListeners { isSomeInstanceSelected(true) }
        }
        _focusedView.set(_selectedViews.singleOrNull())
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