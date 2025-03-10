package xenakis.ui.actions

import bundles.PublicProperty
import fxutils.actions.action
import fxutils.actions.collectActions
import org.kordamp.ikonli.material2.Material2AL
import org.kordamp.ikonli.materialdesign2.MaterialDesignR
import reaktive.value.reactiveValue
import xenakis.model.obj.RenamableObject
import xenakis.model.registry.NamedObject
import xenakis.model.registry.ObjectRegistry
import xenakis.ui.controls.RenamePrompt

object RegistryObjectActions {
    fun <O : NamedObject> renameAction(objectType: (O) -> String) =
        action<O>("Rename object") {
            shortcut("F2")
            icon(MaterialDesignR.RENAME_BOX)
            applicableIf { obj -> reactiveValue(obj is RenamableObject && obj.canRename) }
            executes { obj, ev ->
                obj as RenamableObject
                RenamePrompt(obj, "Rename ${objectType(obj)}").showDialog(ev)
            }
        }

    fun <O : NamedObject> deleteAction(registry: PublicProperty<out ObjectRegistry<O>>) = action<O>("Delete object") {
        shortcuts("Ctrl+DELETE")
        icon(Material2AL.DELETE)
        applicableIf { obj -> reactiveValue(obj.canDelete) }
        executes { obj -> obj.context[registry].remove(obj) }
    }

    fun <O : NamedObject> all(registry: PublicProperty<out ObjectRegistry<O>>) = collectActions<O> {
        add(renameAction { o -> o.context[registry].objectType })
        deleteAction(registry)
    }
}