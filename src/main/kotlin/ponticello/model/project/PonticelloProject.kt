package ponticello.model.project

import bundles.set
import fxutils.undo.UndoManager
import hextant.context.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.decodeFromStream
import ponticello.impl.Logger
import ponticello.impl.json
import ponticello.model.flow.AudioFlows
import ponticello.model.obj.ContextualObject
import ponticello.model.score.ScoreObject
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.launcher.ProgressIndicator
import java.io.File

class PonticelloProject private constructor(val components: Map<Component<out ContextualObject>, ContextualObject>) {
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

    val dataDir get() = projectDirectory.resolve("data")

    fun initialize(context: Context) {
        this.context = context
        client = context[SuperColliderClient]
        for (comp in allComponents) {
            get(comp).initialize(context)
        }
    }

    fun saveTo(projectDirectory: File): Boolean {
        for (inst in mainScore.allInstances()) {
            if (inst.obj !is ScoreObject.Unresolved && inst.obj !in objects) {
                Logger.warn("Had to add object for $inst", Logger.Category.Project)
                objects.add(inst.obj)
            }
        }
        try {
            get(UI_STATE).saveWindowStates()
        } catch (e: Exception) {
            Logger.error("Failed to save window states!", e)
        }
        this.projectDirectory = projectDirectory
        dataDir.mkdirs()
        var savedAllComponents = true
        for ((component, _) in components) {
            val ok = save(component)
            if (!ok) savedAllComponents = false
        }
        if (savedAllComponents) context[UndoManager].savedChanges()
        return savedAllComponents
    }

    fun save(component: Component<out ContextualObject>): Boolean {
        val file = dataDir.resolve("${component.name}.json")
        val value = components.getValue(component)
        try {
            val str = json.encodeToString(component.serializer as KSerializer<ContextualObject>, value)
            file.writeText(str)
            return true
        } catch (e: Exception) {
            Logger.error("Error while saving ${component.name} to $file!")
            e.printStackTrace()
            return false
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
        context[AudioFlows].syncAll()
        Logger.confirm("Synchronized with SuperCollider", Logger.Category.Project)
    }

    fun rebootServer() {
        get(SERVER_OPTIONS).reboot(context[SuperColliderClient])
    }

    fun hasInstancesOf(obj: ScoreObject): Boolean = mainScore.hasInstancesOf(obj) ||
            get(LAUNCHER_GRID).items().any { item -> item.target.targetObject == obj }

    companion object {
        val projectDirectory = bundles.publicProperty<File>("Project directory")

        fun loadFrom(
            folder: File,
            indicator: ProgressIndicator,
            targetProgress: Double,
        ): PonticelloProject {
            val data = folder.resolve("data")
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
            val project = PonticelloProject(components)
            project.projectDirectory = folder
            return project
        }

        fun create(location: File, context: Context): PonticelloProject {
            val components = allComponents.associateWith { (_, _, default) -> default() }
            val project = PonticelloProject(components)
            context[projectDirectory] = location
            project.projectDirectory = location
            project.initialize(context)
            return project
        }
    }
}