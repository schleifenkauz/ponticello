package ponticello.model.project

import bundles.set
import fxutils.undo.UndoManager
import hextant.context.Context
import ponticello.impl.Logger
import ponticello.model.flow.AudioFlows
import ponticello.model.obj.ContextualObject
import ponticello.model.score.ScoreObject
import ponticello.model.score.UnresolvedScoreObject
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.dock.AppLayout
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject
import ponticello.ui.launcher.ProgressIndicator
import reaktive.value.now
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
        context[currentProject] = this
        this.context = context
        client = context[SuperColliderClient]
        for (comp in allComponents) {
            get(comp).initialize(context)
        }
    }

    fun saveTo(projectDirectory: File): Boolean {
        for (inst in mainScore.allInstances()) {
            if (inst.obj !is UnresolvedScoreObject && !objects.has(inst.obj.name.now)) {
                Logger.warn("Had to add object for $inst", Logger.Category.Project)
                objects.add(inst.obj)
            }
        }
        if (context.hasProperty(AppLayout)) {
            try {
                context[AppLayout].saveLayoutState()
            } catch (e: Exception) {
                Logger.error("Failed to save layout state!", e)
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
        component as Component<ContextualObject>
        val file = dataDir.resolve("${component.name}.json")
        val value = components.getValue(component)
        try {
            component.onSave(value)
            component.serializer.serializeComponent(value, dataDir)
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

    fun openInExplorer() {
        val os = System.getProperty("os.name").lowercase()
        when {
            os.contains("win") -> Runtime.getRuntime().exec("explorer.exe /select,$projectDirectory")
            os.contains("nix") || os.contains("nux") || os.contains("aix") ->
                Runtime.getRuntime().exec("xdg-open $projectDirectory")
            os.contains("mac") -> Runtime.getRuntime().exec("open $projectDirectory")
        }
    }

    companion object {
        val projectDirectory = bundles.publicProperty<File>("Project directory")

        fun loadFrom(
            folder: File,
            indicator: ProgressIndicator,
            targetProgress: Double,
        ): PonticelloProject {
            val data = folder.resolve("data")
            val progressPerComponent = (targetProgress - indicator.progress) / allComponents.size
            val components = allComponents.associateWith { component ->
                indicator.increaseProgress(progressPerComponent, "Loading ${component.name}")
                component.serializer.deserializeComponent(data)
            }
            val project = PonticelloProject(components)
            project.projectDirectory = folder
            return project
        }

        fun create(location: File, context: Context): PonticelloProject {
            val components = allComponents.associateWith { component -> component.default() }
            val project = PonticelloProject(components)
            context[projectDirectory] = location
            project.projectDirectory = location
            project.initialize(context)
            return project
        }
    }
}