package xenakis.model

import bundles.publicProperty
import bundles.set
import hextant.context.Context
import hextant.context.withoutUndo
import hextant.serial.EditorRoot
import hextant.serial.readJson
import hextant.serial.writeJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.SuperColliderClient
import xenakis.model.Score.Companion.ROOT_SCORE_NAME
import xenakis.model.SuperColliderObject.LiveCycleType
import xenakis.sc.CodeBlock
import xenakis.sc.editor.CodeBlockEditor
import xenakis.ui.XenakisController
import java.io.File

@Serializable
class XenakisProject private constructor(
    val settings: InteractionSettings,
    val groups: GroupRegistry,
    val busses: BusRegistry,
    val buffers: BufferRegistry,
    val samples: SampleRegistry,
    val instruments: InstrumentRegistry,
    val flowGraph: AudioFlowGraph,
    val globalControls: GlobalControls,
    val serverSetup: EditorRoot<CodeBlockEditor>,
    val serverTree: EditorRoot<CodeBlockEditor>,
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

    fun initialize(context: Context) {
        this.context = context
        client = context[SuperColliderClient]
        client.run {
            instruments.run { allocateAll() }
            flowGraph.run {  }
            updateSetupCode(serverSetup.editor.result.now, LiveCycleType.ServerBoot)
            updateSetupCode(serverTree.editor.result.now, LiveCycleType.ServerTree)
        }
    }

    fun saveTo(projectDirectory: File) {
        val data = projectDirectory.resolve("xenakis_data")
        data.resolve("settings.json").writeJson(settings)
        data.resolve("groups.json").writeJson(groups)
        data.resolve("busses.json").writeJson(busses)
        data.resolve("buffers.json").writeJson(buffers)
        data.resolve("samples.json").writeJson(samples)
        data.resolve("instruments.json").writeJson(instruments)
        data.resolve("flow_graph.json").writeJson(flowGraph)
        data.resolve("global_controls.json").writeJson(globalControls)
        data.resolve("server_setup.json").writeJson(serverSetup)
        data.resolve("server_tree.json").writeJson(serverTree)
        data.resolve("score.json").writeJson(score)
    }

    fun updateSetupCode(setupCode: CodeBlock, liveCycleType: LiveCycleType) {
        val funcName = "~setup$liveCycleType"
        client.run {
            +"if ($funcName != nil) { $liveCycleType.remove(~setup$liveCycleType) }"
            append("$funcName = ")
            appendBlock {
                setupCode.writeCode(writer, context)
            }
            appendLine(";")
            +"$liveCycleType.add($funcName, s)"
        }
    }

    companion object {
        val projectDirectory = publicProperty<File>("Project directory")

        fun loadFrom(folder: File, context: Context, listener: XenakisController): XenakisProject {
            context[projectDirectory] = folder
            val data = folder.resolve("xenakis_data")
            context.withoutUndo {
                val settings = data.resolve("settings.json").readJson<InteractionSettings>()
                val groups = data.resolve("groups.json").readJson<GroupRegistry>()
                groups.initialize(context)
                listener.setProgress(0.45, "Loading busses")
                val busses = data.resolve("busses.json").readJson<BusRegistry>()
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
                listener.setProgress(0.75, "Loading server setup code")
                val serverSetup = data.resolve("server_setup.json").readJson<EditorRoot<CodeBlockEditor>>()
                val beforePlay = data.resolve("server_tree.json").readJson<EditorRoot<CodeBlockEditor>>()
                listener.setProgress(0.9, "Loading score")
                val score = data.resolve("score.json").readJson<Score>()
                listener.setProgress(0.9, "Ready")
                score.initialize(context, reactiveValue(ROOT_SCORE_NAME))
                return XenakisProject(
                    settings,
                    groups, busses, buffers, samples, instruments,
                    flowGraph, globalControls,
                    serverSetup, beforePlay,
                    score
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
            instruments = InstrumentRegistry.newInstance().also { r -> r.initialize(context) },
            flowGraph = AudioFlowGraph.createDefault().also { g -> g.initialize(context) },
            globalControls = GlobalControls(mutableListOf()).also { c -> c.initialize(context) },
            serverSetup = EditorRoot.create(CodeBlockEditor(context)),
            serverTree = EditorRoot.create(CodeBlockEditor(context)),
            score = Score().also { score -> score.initialize(context, reactiveValue(ROOT_SCORE_NAME)) },
        ).also { project ->
            context[projectDirectory] = location
            project.initialize(context)
            project.projectDirectory = location
        }
    }
}