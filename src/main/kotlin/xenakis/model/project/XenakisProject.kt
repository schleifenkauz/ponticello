package xenakis.model.project

import bundles.set
import hextant.context.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import xenakis.impl.Logger
import xenakis.impl.json
import xenakis.model.obj.ContextualObject
import xenakis.sc.client.SuperColliderClient
import java.io.File

class XenakisProject private constructor(val components: Map<Component<out ContextualObject>, ContextualObject>) {
    @kotlinx.serialization.Transient
    lateinit var context: Context
        private set

    @kotlinx.serialization.Transient
    lateinit var client: SuperColliderClient
        private set

    @kotlinx.serialization.Transient
    lateinit var projectDirectory: File
        private set

    val name: String get() = projectDirectory.name

    val dataDir get() = projectDirectory.resolve("xenakis_data")

    fun initialize(context: Context) {
        this.context = context
        client = context[SuperColliderClient]
        for (comp in allComponents) {
            get(comp).initialize(context)
        }
    }

    fun saveTo(projectDirectory: File) {
        for (inst in score.allInstances()) {
            if (inst.obj !in objects) {
                Logger.warn("Had to readd object for $inst", Logger.Category.Project)
                objects.add(inst.obj)
            }
        }
        this.projectDirectory = projectDirectory
        dataDir.mkdirs()
        for ((component, _) in components) {
            save(component)
        }
    }

    fun save(component: Component<out  ContextualObject>) {
        val file = dataDir.resolve("${component.name}.json")
        val stream = file.outputStream().buffered()
        val value = components.getValue(component)
        try {
            json.encodeToStream(component.serializer as KSerializer<ContextualObject>, value, stream)
        } catch (e: Exception) {
            Logger.error("Error while saving ${component.name} to $file!")
            e.printStackTrace()
        } finally {
            stream.close()
        }
    }


    fun save(obj: ContextualObject) {
        val component = components.entries.find { (_, value) -> value == obj }
            ?: error("$obj is not a component of the project")
        save(component.key)
    }

    fun syncWithSuperCollider() = client.run {
        instruments.syncAll()
        get(BUFFERS).syncAll()
        busses.syncAll()
        samples.syncAll()
        get(FLOWS).syncAll()
        Logger.confirm("Synchronized with SuperCollider", Logger.Category.Project)
    }

    fun rebootServer() {
        get(SERVER_OPTIONS).reboot(context)
    }

    companion object {
        val projectDirectory = bundles.publicProperty<File>("Project directory")

        fun loadFrom(
            folder: File,
            context: Context,
            indicator: xenakis.ui.launcher.ProgressIndicator
        ): XenakisProject {
            context[projectDirectory] = folder
            val data = folder.resolve("xenakis_data")
            val components = allComponents.associateWith { (name, serializer, default) ->
                val file = data.resolve("$name.json")
                if (file.isFile) {
                    val stream = file.inputStream().buffered()
                    try {
                        json.decodeFromStream(serializer, stream)
                    } catch (e: Exception) {
                        Logger.error("Error while reading component $name from $file!", detailMessage = e.message)
                        default()
                    } finally {
                        stream.close()
                    }
                } else default()
            }
            //TODO update indicator while reading
            val project = XenakisProject(components)
            project.projectDirectory = folder
            project.initialize(context)
            return project
        }

        fun create(location: File, context: Context): XenakisProject {
            val components = allComponents.associateWith { (_, _, default) -> default() }
            val project = XenakisProject(components)
            context[projectDirectory] = location
            project.projectDirectory = location
            project.initialize(context)
            return project
        }
    }
}