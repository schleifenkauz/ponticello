package xenakis.model.registry

import bundles.PublicProperty
import bundles.publicProperty
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.impl.async
import xenakis.impl.json
import xenakis.model.obj.CustomizableSynthDefObject
import java.io.File

@Suppress("OPT_IN_USAGE")
class GlobalSynthDefLib(private val directory: File) {
    init {
        directory.mkdirs()
    }

    fun getNames(): List<String> =
        if (!(directory.isDirectory)) emptyList()
        else directory.listFiles()!!.filter { it.endsWith(".json") }.map { it.nameWithoutExtension }

    fun get(name: String): CustomizableSynthDefObject? {
        val file = jsonFile(name)
        if (!file.isFile) return null
        val stream = file.inputStream().buffered()
        val def: CustomizableSynthDefObject = json.decodeFromStream(stream)
        return def
    }

    fun push(synthDef: CustomizableSynthDefObject) {
        val name = synthDef.name.now
        val file = jsonFile(name)
        async {
            val stream = file.outputStream().buffered()
            try {
                json.encodeToStream(synthDef, stream)
            } catch (ex: Exception) {
                Logger.error(
                    "Error while saving SynthDef '$name' to global library",
                    ex, Logger.Category.Registries
                )
            } finally {
                stream.close()
            }
        }
    }

    fun has(name: String): Boolean = jsonFile(name).isFile

    private fun jsonFile(name: String): File = directory.resolve("$name.json")

    companion object : PublicProperty<GlobalSynthDefLib> by publicProperty("GlobalSynthDefLib")
}