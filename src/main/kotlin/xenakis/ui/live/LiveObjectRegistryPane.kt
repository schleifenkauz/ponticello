package xenakis.ui.live

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import javafx.geometry.Orientation
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import reaktive.value.binding.map
import xenakis.model.live.LiveObject
import xenakis.model.obj.SuperColliderObject
import xenakis.model.registry.ObjectRegistry
import xenakis.ui.registry.ObjectBox
import xenakis.ui.registry.ObjectRegistryPane

abstract class LiveObjectRegistryPane<O : LiveObject>(registry: ObjectRegistry<O>) : ObjectRegistryPane<O>(registry) {
    override val inlineOrientation: Orientation
        get() = Orientation.HORIZONTAL

    override fun getActions(box: ObjectBox<O>): List<ContextualizedAction> = actions.withContext(box.obj)

    companion object {
        @JvmStatic
        protected val actions = collectActions<LiveObject> {
            addAll(SuperColliderObject.actions) { it }
            addAction("Play/pause") {
                icon { obj -> obj.isActive.map { active -> if (active) MaterialDesignP.PAUSE else MaterialDesignP.PLAY } }
                shortcut("Ctrl+SPACE")
                executes { obj -> obj.toggleActive() }
            }
            addAction("Reset") {
                icon(MaterialDesignS.STOP)
                executes { obj -> obj.reset() }
            }
        }
    }
}