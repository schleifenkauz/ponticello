package xenakis.model.project

import bundles.set
import hextant.context.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.decodeFromStream
import xenakis.impl.Logger
import xenakis.impl.json
import xenakis.model.flow.AudioFlowGraph
import xenakis.model.obj.ContextualObject
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.launcher.ProgressIndicator
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
                Logger.warn("Had to add object for $inst", Logger.Category.Project)
                objects.add(inst.obj)
            }
        }
        get(UI_STATE).saveWindowStates()
        this.projectDirectory = projectDirectory
        dataDir.mkdirs()
        for ((component, _) in components) {
            save(component)
        }
    }

    fun save(component: Component<out  ContextualObject>) {
        val file = dataDir.resolve("${component.name}.json")
        val value = components.getValue(component)
        try {
            val str = json.encodeToString(component.serializer as KSerializer<ContextualObject>, value)
            file.writeText(str)
        } catch (e: Exception) {
            Logger.error("Error while saving ${component.name} to $file!")
            e.printStackTrace()
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
        buffers.syncAll()
        context[AudioFlowGraph].syncAll()
        Logger.confirm("Synchronized with SuperCollider", Logger.Category.Project)
    }

    fun rebootServer() {
        get(SERVER_OPTIONS).reboot(context[SuperColliderClient])
    }

    companion object {
        val projectDirectory = bundles.publicProperty<File>("Project directory")

        fun loadFrom(
            folder: File,
            indicator: ProgressIndicator,
            targetProgress: Double
        ): XenakisProject {
            val data = folder.resolve("xenakis_data")
            val progressPerComponent = (targetProgress - indicator.progress) / allComponents.size
            val components = allComponents.associateWith { (name, serializer, default) ->
                indicator.increaseProgress(progressPerComponent, "Loading $name")
                val file = data.resolve("$name.json")
                if (file.isFile) {
                    val stream = file.inputStream().buffered()
                    try {
                        json.decodeFromStream(serializer, stream)
                    } catch (e: Exception) {
                        Logger.error("Error while reading component $name from $file!", e)
                        default()
                    } finally {
                        stream.close()
                    }
                } else default()
            }
            val project = XenakisProject(components)
            project.projectDirectory = folder
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