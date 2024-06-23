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

    fun saveTo(folder: File) {
        folder.resolve("settings.json").writeJson(settings)
        folder.resolve("groups.json").writeJson(groups)
        folder.resolve("busses.json").writeJson(busses)
        folder.resolve("buffers.json").writeJson(buffers)
        folder.resolve("samples.json").writeJson(samples)
        folder.resolve("instruments.json").writeJson(instruments)
        folder.resolve("flow_graph.json").writeJson(flowGraph)
        folder.resolve("global_controls.json").writeJson(globalControls)
        folder.resolve("server_setup.json").writeJson(serverSetup)
        folder.resolve("server_tree.json").writeJson(serverTree)
        folder.resolve("score.json").writeJson(score)
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
            context.withoutUndo {
                val settings = folder.resolve("settings.json").readJson<InteractionSettings>()
                val groups = folder.resolve("groups.json").readJson<GroupRegistry>()
                groups.initialize(context)
                listener.setProgress(0.45, "Loading busses")
                val busses = folder.resolve("busses.json").readJson<BusRegistry>()
                busses.initialize(context)
                listener.setProgress(0.5, "Loading buffers")
                val buffers = folder.resolve("buffers.json").readJson<BufferRegistry>()
                buffers.initialize(context)
                listener.setProgress(0.55, "Loading samples")
                val samples = folder.resolve("samples.json").readJson<SampleRegistry>()
                samples.initialize(context)
                listener.setProgress(0.6, "Loading instruments")
                val instruments = folder.resolve("instruments.json").readJson<InstrumentRegistry>()
                instruments.initialize(context)
                listener.setProgress(0.65, "Loading audio flow graph")
                val flowGraph = folder.resolve("flow_graph.json").readJson<AudioFlowGraph>()
                flowGraph.initialize(context)
                listener.setProgress(0.7, "Loading global controls")
                val globalControls = folder.resolve("global_controls.json").readJson<GlobalControls>()
                globalControls.initialize(context)
                listener.setProgress(0.75, "Loading server setup code")
                val serverSetup = folder.resolve("server_setup.json").readJson<EditorRoot<CodeBlockEditor>>()
                val beforePlay = folder.resolve("server_tree.json").readJson<EditorRoot<CodeBlockEditor>>()
                listener.setProgress(0.9, "Loading score")
                val score = folder.resolve("score.json").readJson<Score>()
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