package ponticello.model.obj

import fxutils.actions.collectActions
import org.kordamp.ikonli.material2.Material2MZ
import ponticello.impl.Logger
import ponticello.sc.client.ScWriter
import reaktive.value.now

interface SuperColliderObject : NamedObject {
    val superColliderName: String

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
                executes { obj ->
                    obj.sync()
                    Logger.confirm(
                        "Updated ${obj.registry?.objectType ?: "object"}: ${obj.name.now}",
                        Logger.Category.Registries
                    )
                }
            }
        }
    }
}

