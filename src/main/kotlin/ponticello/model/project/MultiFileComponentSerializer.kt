package ponticello.model.project

import hextant.serial.readJson
import hextant.serial.writeJson
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import ponticello.impl.Logger
import ponticello.impl.json
import ponticello.model.registry.NamedObject
import ponticello.model.registry.NamedObjectList
import ponticello.model.registry.ObjectListSerializer
import reaktive.value.now
import java.io.File

class MultiFileComponentSerializer<T : NamedObject, L : NamedObjectList<T>>(
    private val itemSerializer: KSerializer<T>, listSerializer: KSerializer<L>,
    private val listConstructor: (MutableList<T>) -> L,
    private val extension: String = "json",
) : SingleFileComponentSerializer<L>(listSerializer) {
    override fun serializeComponent(value: L, dataDirectory: File) {
        val subDir = dataDirectory.resolve(component.name)
        subDir.mkdirs()
        var everythingOk = true
        val names = value.map { obj -> obj.name.now }
        val registryFile = subDir.resolve("registry.json")
        try {
            registryFile.writeJson(names, json)
        } catch (e: Exception) {
            Logger.error("Error while writing object names of '${component.name}' to $registryFile!", e)
            everythingOk = false
        }
        for (obj in value) {
            val objName = obj.name.now
            val file = subDir.resolve("$objName.$extension")
            try {
                file.writeJson(itemSerializer, obj, json)
            } catch (e: Exception) {
                everythingOk = false
                Logger.error("Error while writing item $objName of component '${component.name}' to $file!", e)
            }
        }
        for (file in subDir.listFiles() ?: emptyArray()) {
            if (file.name == "registry.json" || file.extension != extension) continue
            val objName = file.nameWithoutExtension
            if (!value.has(objName)) {
                try {
                    file.delete()
                } catch (e: Exception) {
                    Logger.error("Error while deleting item $objName of component '${component.name}' from $file!", e)
                }
            }
        }
        val singleFile = dataDirectory.resolve("${component.name}.json")
        if (everythingOk && singleFile.isFile) {
            singleFile.delete()
        }
    }

    override fun deserializeComponent(dataDirectory: File): L {
        val subDir = dataDirectory.resolve(component.name)
        if (!subDir.isDirectory) return super.deserializeComponent(dataDirectory)
        val registryFile = subDir.resolve("registry.json")
        val names = if (registryFile.isFile) {
            try {
                registryFile.readJson<List<String>>(json)
            } catch (e: Exception) {
                Logger.error("Failed to read object names of '${component.name}' from $registryFile!", e)
                return listConstructor(mutableListOf())
            }
        } else {
            subDir.listFiles { f -> f.extension == extension }?.map { f -> f.nameWithoutExtension } ?: emptyList()
        }
        val list = mutableListOf<T>()
        for (name in names) {
            val file = subDir.resolve("$name.$extension")
            if (!file.isFile) {
                Logger.error("File $file is missing!")
                continue
            }
            try {
                val obj = file.readJson(itemSerializer, json)
                list.add(obj)
            } catch (e: Exception) {
                Logger.error("Error while reading item of component '${component.name}' from $file!", e)
            }
        }
        return listConstructor(list)
    }

    companion object {
        inline operator fun <reified T : NamedObject, reified L : NamedObjectList<T>> invoke(
            noinline listConstructor: (MutableList<T>) -> L,
            itemSerializer: KSerializer<T> = serializer(),
            listSerializer: KSerializer<L> = ObjectListSerializer(itemSerializer, listConstructor),
            extension: String = "json",
        ): MultiFileComponentSerializer<T, L> =
            MultiFileComponentSerializer(
            itemSerializer, listSerializer, listConstructor, extension
        )
    }
}