package xenakis.model.registry

import bundles.publicProperty
import fxutils.prompt.YesNoPrompt
import javafx.application.Platform
import javafx.event.Event
import kotlinx.serialization.KSerializer
import reaktive.value.now
import xenakis.impl.Logger
import xenakis.impl.async
import xenakis.impl.json
import xenakis.model.obj.CustomizableSynthDefObject
import xenakis.model.obj.ProcessDefObject
import java.io.File

class GlobalDefinitionLibrary<T: NamedObject>(
    private val directory: File,
    private val serializer: KSerializer<T>,
    val objectType: String
) {
    init {
        directory.mkdirs()
    }

    fun getNames(): List<String> =
        if (!(directory.isDirectory)) emptyList()
        else directory.listFiles()!!.filter { it.extension == "json" }.map { it.nameWithoutExtension }

    fun get(name: String): T? {
        val file = jsonFile(name)
        if (!file.isFile) return null
        val text = file.readText()
        return json.decodeFromString(serializer, text)
    }

    fun push(def: T) {
        val name = def.name.now
        val file = jsonFile(name)
        async {
            try {
                val str = json.encodeToString(serializer, def)
                file.writeText(str)
            } catch (ex: Exception) {
                Logger.error(
                    "Error while saving $objectType '$name' to global library",
                    ex, Logger.Category.Registries
                )
            }         }
    }

    fun has(name: String): Boolean = jsonFile(name).isFile

    private fun jsonFile(name: String): File = directory.resolve("$name.json")

    fun saveToGlobalLib(obj: T, ev: Event?) {
        val name = obj.name.now
        if (!has(name) ||
            YesNoPrompt(
                "Overwrite SynthDef $name in global library?",
                default = true
            ).showDialog(ev) == true
        ) {
            async {
                push(obj)
                Platform.runLater {
                    Logger.confirm(
                        "Saved SynthDef '${obj.name.now}' to global library.",
                        Logger.Category.Instruments
                    )
                }
            }
        }
    }

    companion object {
        val synthDefs = publicProperty<GlobalDefinitionLibrary<CustomizableSynthDefObject>>("synthDefs")
        val processDefs = publicProperty<GlobalDefinitionLibrary<ProcessDefObject>>("processDefs")
    }
}