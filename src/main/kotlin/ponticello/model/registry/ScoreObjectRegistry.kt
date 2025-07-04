package ponticello.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import bundles.set
import hextant.context.Context
import javafx.event.Event
import kotlinx.serialization.Serializable
import ponticello.impl.Logger
import ponticello.model.project.UIState
import ponticello.model.score.ScoreObject
import ponticello.model.score.UnresolvedScoreObject
import ponticello.ui.controls.NamePrompt
import reaktive.value.now

@Serializable
class ScoreObjectRegistry(
    override val objects: MutableList<ScoreObject> = mutableListOf(),
) : ObjectRegistry<ScoreObject>() {
    override val objectType: String
        get() = "Score Object"

    override fun initialize(context: Context) {
        context[ScoreObjectRegistry] = this
        super.initialize(context)
    }

    override fun add(obj: ScoreObject, idx: Int) {
        if (obj is UnresolvedScoreObject) {
            Logger.error("Attempted to add unresolved object to ScoreObjectRegistry")
            return
        }
        super.add(obj, idx)
    }

    fun nameForClone(obj: ScoreObject): String {
        val name = obj.name.now
        val prefix = name.dropLastWhile { it.isDigit() }.removeSuffix("_")
        return availableName(prefix)
    }

    fun nameForClone(obj: ScoreObject, ev: Event?): String? {
        val defaultName = nameForClone(obj)
        return if (context[UIState].askForCloneNames.now) {
            NamePrompt(context[ScoreObjectRegistry], "Name for clone", defaultName)
                .showDialog(ev, preferMouseCoords = true)
        } else defaultName
    }

    fun nameForGroup(ev: Event?): String? {
        val defaultName = availableName("group")
        return if (context[UIState].askForGroupNames.now) {
            NamePrompt(context[ScoreObjectRegistry], "Name for group", defaultName)
                .showDialog(ev, preferMouseCoords = true)
        } else defaultName
    }

    companion object : PublicProperty<ScoreObjectRegistry> by publicProperty("ScoreObjectRegistry") {
        fun createDefault() = ScoreObjectRegistry()
    }
}