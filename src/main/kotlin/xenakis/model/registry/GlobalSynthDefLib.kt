package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import hextant.context.Context
import hextant.context.withoutUndo
import hextant.serial.readJson
import hextant.serial.writeJson
import reaktive.value.now
import xenakis.impl.async
import xenakis.model.obj.CustomizableSynthDefObject
import java.io.File

class GlobalSynthDefLib(private val context: Context, private val file: File) {
    private var collection = mutableListOf<CustomizableSynthDefObject>()
    private var lastReloaded = 0L

    fun reload() {
        if (file.lastModified() < lastReloaded) return
        collection =
            if (file.exists()) context.withoutUndo { file.readJson<MutableList<CustomizableSynthDefObject>>() }
            else mutableListOf(CustomizableSynthDefObject.sine())
        lastReloaded = System.currentTimeMillis()
    }

    fun get(): List<CustomizableSynthDefObject> = collection

    fun get(name: String) = get().find { instr -> instr.name.now == name }

    fun push(synthDef: CustomizableSynthDefObject) {
        reload()
        val defs = collection.toMutableList()
        defs.removeIf { def -> def.name.now == synthDef.name.now }
        defs.add(synthDef)
        async {
            file.writeJson(defs)
        }
    }

    fun has(name: String): Boolean = collection.any { it.name.now == name }

    companion object : PublicProperty<GlobalSynthDefLib> by publicProperty("GlobalSynthDefLib")
}