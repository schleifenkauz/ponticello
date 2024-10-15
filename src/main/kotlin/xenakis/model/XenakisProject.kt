package xenakis.model

import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.context.withoutUndo
import hextant.serial.readJson
import hextant.serial.writeJson
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.model.obj.SuperColliderObject.LiveCycleType
import xenakis.model.registry.*
import xenakis.model.score.Score
import xenakis.model.score.Score.Companion.ROOT_SCORE_NAME
import xenakis.sc.CodeBlock
import xenakis.sc.client.SuperColliderClient
import xenakis.ui.XenakisController
import java.io.File

class XenakisProject private constructor(
    val settings: InteractionSettings,
    val groups: GroupRegistry,
    val busses: BusRegistry,
    val buffers: BufferRegistry,
    val samples: SampleRegistry,
    val instruments: InstrumentRegistry,
    val flowGraph: AudioFlowGraph,
    val globalControls: GlobalControls,
    val processDefs: ProcessDefRegistry,
    val setupCode: SetupCode,
    val serverOptions: ServerOptions,
    val objects: ScoreObjectRegistry,
    val score: Score
) {
    @Transient
    lateinit var context: Context
        private set

    @Transient
    lateinit var client: SuperColliderClient
        private set

    @Transient
    lateinit var projectDirectory: File
        private set

    val dataDir get() = projectDirectory.resolve("xenakis_data")

    val components
        get() = listOf(
            settings,
            groups, busses, buffers, samples, instruments,
            flowGraph, globalControls, objects, setupCode, serverOptions,
            score
        )

    fun initialize(context: Context) {
        this.context = context
        client = context[SuperColliderClient]
        client.run {
            updateSetupCode(setupCode.serverSetup.editor.result.now, LiveCycleType.ServerBoot)
            updateSetupCode(setupCode.serverTree.editor.result.now, LiveCycleType.ServerTree)
        }
    }

    fun saveTo(projectDirectory: File) {
        this.projectDirectory = projectDirectory
        dataDir.mkdirs()
        for (component in components) save(component)
    }

    @Suppress("UNCHECKED_CAST")
    @OptIn(InternalSerializationApi::class)
    inline fun <reified T : ProjectComponent> save(component: T) {
        val serializer = component::class.serializer() as KSerializer<T>
        dataDir.resolve("${component.componentName}.json").writeJson(serializer, component, json)
    }

    fun updateSetupCode(setupCode: CodeBlock, liveCycleType: LiveCycleType) {
        val funcName = "~setup$liveCycleType"
        client.run {
            +"if ($funcName != nil) { $liveCycleType.remove(~setup$liveCycleType) }"
            append("$funcName = ")
            appendBlock {
                setupCode.writeCode(writer, context)
            }
            +"$liveCycleType.add($funcName, s)"
        }
    }

    fun syncWithSuperCollider() = client.run {
        groups.syncAll(writer)
        instruments.syncAll(writer)
        buffers.syncAll(writer)
        busses.syncAll(writer)
        samples.syncAll(writer)
        flowGraph.redefineAudioFlow(writer)
        globalControls.setBusValues(writer)
        Logger.confirm("Synchronized with SuperCollider", Logger.Category.Project)
    }

    interface ProjectComponent {
        val componentName: String
    }

    companion object {
        val json = Json {
            prettyPrint = true
        }

        val projectDirectory = publicProperty<File>("Project directory")

        fun loadFrom(folder: File, context: Context, listener: XenakisController): XenakisProject {
            context[projectDirectory] = folder
            val data = folder.resolve("xenakis_data")
            context.withoutUndo {
                val settings = data.resolve("settings.json").readJson<InteractionSettings>()
                val groups = data.resolve("groups.json").readJson<GroupRegistry>()
                groups.initialize(context)
                listener.setProgress(0.45, "Loading busses")
                val busses = data.resolve("buses.json").readJson<BusRegistry>()
                busses.initialize(context)
                listener.setProgress(0.5, "Loading buffers")
                val buffers = data.resolve("buffers.json").readJson<BufferRegistry>()
                buffers.initialize(context)
                listener.setProgress(0.55, "Loading samples")
                val samples = data.resolve("samples.json").readJson<SampleRegistry>(Json { ignoreUnknownKeys = true })
                samples.initialize(context)
                listener.setProgress(0.6, "Loading instruments")
                val instruments = data.resolve("instruments.json").readJson<InstrumentRegistry>()
                instruments.initialize(context)
                listener.setProgress(0.65, "Loading audio flow graph")
                val flowGraph = data.resolve("flow_graph.json").readJson<AudioFlowGraph>()
                flowGraph.initialize(context)
                listener.setProgress(0.7, "Loading global controls")
                val globalControls = data.resolve("global_controls.json").readJson<GlobalControls>()
                globalControls.initialize(context)
                val processDefs = data.resolve("process_defs.json").readJson<ProcessDefRegistry>()
                processDefs.initialize(context)
                listener.setProgress(0.75, "Loading server setup code")
                val setupCode = data.resolve("setup_code.json").readJson<SetupCode>()
                val serverOptions = data.resolve("server_options.json").readJson<ServerOptions>()
                serverOptions.initialize(context)
                val objects = data.resolve("score_objects.json").readJson<ScoreObjectRegistry>()
                objects.initialize(context)
                listener.setProgress(0.9, "Loading score")
                val score = data.resolve("score.json").readJson<Score>()
                listener.setProgress(0.9, "Ready")
                score.initialize(context, reactiveValue(ROOT_SCORE_NAME))
                return XenakisProject(
                    settings,
                    groups, busses, buffers, samples, instruments,
                    flowGraph, globalControls, processDefs, setupCode, serverOptions,
                    objects, score
                ).also { p ->
                    p.initialize(context)
                    p.projectDirectory = folder
                }
            }
        }

        fun create(location: File, context: Context) = XenakisProject(
            settings = InteractionSettings.default(),
            groups = GroupRegistry.createDefault().also { r -> r.initialize(context) },
            busses = BusRegistry.createDefault().also { r -> r.initialize(context) },
            buffers = BufferRegistry(mutableListOf()).also { r -> r.initialize(context) },
            samples = SampleRegistry(mutableListOf()).also { r -> r.initialize(context) },
            instruments = InstrumentRegistry.createDefault().also { r -> r.initialize(context) },
            flowGraph = AudioFlowGraph.createDefault().also { g -> g.initialize(context) },
            globalControls = GlobalControls(mutableListOf()).also { c -> c.initialize(context) },
            processDefs = ProcessDefRegistry(mutableListOf()).also { r -> r.initialize(context) },
            setupCode = SetupCode.default(context), serverOptions = ServerOptions(),
            objects = ScoreObjectRegistry(mutableListOf()).also { r -> r.initialize(context) },
            score = Score().also { score -> score.initialize(context, reactiveValue(ROOT_SCORE_NAME)) },
        ).also { project ->
            context[projectDirectory] = location
            project.initialize(context)
            project.projectDirectory = location
        }
    }
}