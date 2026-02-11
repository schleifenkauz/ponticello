package ponticello.model.project

import bundles.set
import fxutils.undo.UndoManager
import hextant.context.Context
import ponticello.impl.Logger
import ponticello.model.flow.AudioFlows
import ponticello.model.git.ProjectGitRepository
import ponticello.model.git.ProjectVersionControl
import ponticello.model.obj.ContextualObject
import ponticello.model.score.ScoreObject
import ponticello.model.score.UnresolvedScoreObject
import ponticello.model.score.controls.EnvelopeControl
import ponticello.sc.client.SuperColliderClient
import ponticello.ui.dock.AppLayout
import ponticello.ui.launcher.PonticelloLauncher.Companion.currentProject
import ponticello.ui.launcher.ProgressIndicator
import reaktive.value.ReactiveValue
import reaktive.value.now
import reaktive.value.reactiveVariable
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

    val name: String get() = getName(projectDirectory)

    val dataDir get() = projectDirectory.resolve("data")

    private var _versionControl = reactiveVariable<ProjectVersionControl?>(null)

    val versionControl: ReactiveValue<ProjectVersionControl?> get() = _versionControl

    fun initialize(context: Context, progressBar: ProgressIndicator, totalDeltaProgress: Double) {
        _versionControl.now = ProjectGitRepository.get(projectDirectory)
        EnvelopeControl.resetCounter()
        context[currentProject] = this
        this.context = context
        client = context[SuperColliderClient]
        for (comp in allComponents) {
            progressBar.increaseProgress(totalDeltaProgress / allComponents.size, "Initializing ${comp.name}")
            try {
                get(comp).initialize(context)
            } catch (e: Exception) {
                Logger.error("Error while initializing ${comp.name}!", e)
            }
        }
    }

    fun save(): Boolean {
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
        dataDir.mkdirs()
        var savedAllComponents = true
        for ((component, _) in components) {
            val ok = save(component)
            if (!ok) savedAllComponents = false
        }
        if (savedAllComponents) context[UndoManager].savedChanges()
        if (savedAllComponents) {
            Logger.confirm("Saved project $name", Logger.Category.Project)
        } else {
            Logger.error("Failed to save project $name", Logger.Category.Project)
        }
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

    fun closeProject() {
        for (component in components) {
            try {
                component.value.dispose()
            } catch (e: Exception) {
                Logger.error("Error while disposing ${component.key.name}!", e)
            }
        }
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
        get(SERVER_OPTIONS).configureOptions(context[SuperColliderClient])
        get(SCRIPTS).get("before_boot").executeContents(context[SuperColliderClient]).join()
        client.run("s.reboot")
    }

    fun hasReferencesTo(obj: ScoreObject): Boolean = when {
        mainScore.hasInstancesOf(obj) -> true
        get(LAUNCHER_GRID).hasReferencesTo(obj) -> true
        get(LIVE_OBJECTS).any { item -> item.hasReferencesTo(obj) } -> true
        else -> false
    }

    fun createGitRepository(): ProjectGitRepository? {
        check(versionControl.now == null) { "Project $name is already a git repository." }
        val repo = ProjectGitRepository.create(this)
        _versionControl.now = repo
        return repo
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
            return project
        }

        fun getName(projectDir: File): String = projectDir.resolve("project.pont")
            .takeIf { f -> f.isFile }
            ?.readText()
            ?.takeIf { txt -> txt.isNotBlank() }
            ?: projectDir.name
    }
}