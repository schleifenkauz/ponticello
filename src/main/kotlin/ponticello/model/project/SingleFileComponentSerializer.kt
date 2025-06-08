package ponticello.model.project

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.decodeFromStream
import ponticello.impl.Logger
import ponticello.impl.json
import java.io.File

open class SingleFileComponentSerializer<T>(val serializer: KSerializer<T>) : ComponentSerializer<T>() {
    override fun serializeComponent(value: T, dataDirectory: File) {
        val file = dataDirectory.resolve("${component.name}.json")
        val str = json.encodeToString(serializer, value)
        file.writeText(str)
    }

    override fun deserializeComponent(dataDirectory: File): T {
        val file = dataDirectory.resolve("${component.name}.json")
        return if (file.isFile) {
            val stream = file.inputStream().buffered()
            try {
                json.decodeFromStream(serializer, stream)
            } catch (e: Exception) {
                Logger.error("Error while reading component ${component.name} from $file!", e)
                component.default()
            } finally {
                stream.close()
            }
        } else component.default()
    }
}