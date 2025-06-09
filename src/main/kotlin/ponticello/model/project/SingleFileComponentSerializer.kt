package ponticello.model.project

import hextant.serial.readJson
import hextant.serial.writeJson
import kotlinx.serialization.KSerializer
import ponticello.impl.Logger
import ponticello.impl.json
import java.io.File

open class SingleFileComponentSerializer<T>(val serializer: KSerializer<T>) : ComponentSerializer<T>() {
    override fun serializeComponent(value: T, dataDirectory: File) {
        val file = dataDirectory.resolve("${component.name}.json")
        file.writeJson(serializer, value, json)
    }

    override fun deserializeComponent(dataDirectory: File): T {
        val file = dataDirectory.resolve("${component.name}.json")
        return if (file.isFile) {
            try {
                file.readJson(serializer, json)
            } catch (e: Exception) {
                Logger.error("Error while reading component ${component.name} from $file!", e)
                component.default()
            }
        } else component.default()
    }
}