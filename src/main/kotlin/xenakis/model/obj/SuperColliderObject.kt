package xenakis.model.obj

import fxutils.actions.collectActions
import org.kordamp.ikonli.material2.Material2MZ
import xenakis.model.registry.NamedObject
import xenakis.sc.Identifier
import xenakis.sc.client.ScWriter

interface SuperColliderObject : NamedObject {
    val superColliderName: String

    val superColliderExpr get() = Identifier(superColliderName)

    fun ScWriter.createObject()

    fun ScWriter.freeObject()

    fun ScWriter.sync() {
        freeObject()
        createObject()
    }

    fun sync()

    enum class LiveCycleType {
        InterpreterBoot, ServerBoot, ServerTree;
    }

    companion object {
        val actions = collectActions<SuperColliderObject> {
            addAction("Sync") {
                icon(Material2MZ.SYNC)
                shortcuts("Ctrl+U")
                executes { obj -> obj.sync() }
            }
        }
    }
}