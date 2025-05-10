package ponticello.ui.live

import fxutils.actions.ContextualizedAction
import fxutils.actions.collectActions
import javafx.geometry.Orientation
import org.kordamp.ikonli.materialdesign2.MaterialDesignP
import org.kordamp.ikonli.materialdesign2.MaterialDesignS
import ponticello.model.live.LiveObject
import ponticello.model.registry.ObjectRegistry
import ponticello.ui.registry.ObjectBox
import ponticello.ui.registry.ObjectRegistryPane
import reaktive.value.binding.map

abstract class LiveObjectRegistryPane<O : LiveObject>(registry: ObjectRegistry<O>) : ObjectRegistryPane<O>(registry) {
    override val inlineOrientation: Orientation
        get() = Orientation.HORIZONTAL

    override fun getActions(box: ObjectBox<O>): List<ContextualizedAction> = actions.withContext(box.obj)

    companion object {
        @JvmStatic
        protected val actions = collectActions<LiveObject> {
            addAction("Sync") {
                icon(MaterialDesignS.SYNC)
                shortcut("Ctrl+U")
                executes { obj -> obj.sync() }
            }
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