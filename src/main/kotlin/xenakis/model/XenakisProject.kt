package xenakis.model

import hextant.context.Context
import hextant.context.withoutUndo
import hextant.serial.EditorRoot
import hextant.serial.readJson
import hextant.serial.writeJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import reaktive.value.now
import reaktive.value.reactiveValue
import xenakis.impl.ScWriter
import xenakis.impl.StatusListener.StatusUpdate
import xenakis.impl.SuperColliderClient
import xenakis.sc.code
import xenakis.sc.editor.CodeBlockEditor
import java.io.File

@Serializable
class XenakisProject private constructor(
    val settings: InteractionSettings,
    val groups: GroupRegistry,
    val busses: BusRegistry,
    val buffers: BufferRegistry,
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
    lateinit var projectFile: File
        private set

    fun initialize(context: Context) {
        this.context = context
        client = context[SuperColliderClient]
        client.statusListener.on(StatusUpdate.ReadyToBoot) {
            client.run {
                addServerBootHooks()
                instruments.run { allocateAll() }
                +"s.boot"
            }
        }
    }

    private fun ScWriter.addServerBootHooks() {
        appendBlock("ServerBoot.add") {
            +serverSetup.editor.result.now.code
        }
        appendLine(";")
    }

    fun saveTo(folder: File) {
        folder.resolve("settings.json").writeJson(settings)
        folder.resolve("groups.json").writeJson(groups)
        folder.resolve("busses.json").writeJson(busses)
        folder.resolve("buffers.json").writeJson(buffers)
        folder.resolve("instruments.json").writeJson(instruments)
        folder.resolve("flow_graph.json").writeJson(flowGraph)
        folder.resolve("global_controls.json").writeJson(globalControls)
        folder.resolve("server_setup.json").writeJson(serverSetup)
        folder.resolve("server_tree.json").writeJson(serverTree)
        folder.resolve("score.json").writeJson(score)
    }

    fun exportAsScript(output: Appendable) {
        with(ScWriter(output)) {
            serverSetup.editor.result.now.code(writer)
            serverTree.editor.result.now.code(writer)
            groups.run { allocateAll() }
            groups.run { allocateAll() }
            flowGraph.run { setupAudioFlow() }
            globalControls.run { setBusValues() }
            instruments.run { allocateAll() }
            score.writePlayerTask(writer, startFrom = 0.0, prefix = "")
        }
    }

    fun ScWriter.playScore(fromTime: Double) {
        appendLine("~synths = ();")
        appendLine("~tasks = ();")
        serverTree.editor.result.now.code(this)
        score.writePlayerTask(this, fromTime, prefix = "")
    }

    fun rebootServer() {
        client.run("s.reboot;")
    }

    companion object {
        fun loadFrom(folder: File, context: Context): XenakisProject {
            context.withoutUndo {
                val settings = folder.resolve("settings.json").readJson<InteractionSettings>()
                val groups = GroupRegistry.createDefault()
                //folder.resolve("groups.json").readJson<GroupRegistry>()
                groups.initialize(context)
                val busses = folder.resolve("busses.json").readJson<BusRegistry>()
                busses.initialize(context)
                val buffers = folder.resolve("buffers.json").readJson<BufferRegistry>()
                buffers.initialize(context)
                val instruments = folder.resolve("instruments.json").readJson<InstrumentRegistry>()
                instruments.initialize(context)
                val flowGraph = folder.resolve("flow_graph.json").readJson<AudioFlowGraph>()
                flowGraph.initialize(context)
                val globalControls = folder.resolve("global_controls.json").readJson<GlobalControls>()
                globalControls.initialize(context)
                val serverSetup = folder.resolve("server_setup.json").readJson<EditorRoot<CodeBlockEditor>>()
                val beforePlay = folder.resolve("server_tree.json").readJson<EditorRoot<CodeBlockEditor>>()
                val score = folder.resolve("score.json").readJson<Score>()
                score.initialize(context, reactiveValue("<root>"))
                return XenakisProject(
                    settings,
                    groups, busses, buffers, instruments,
                    flowGraph, globalControls,
                    serverSetup, beforePlay,
                    score
                ).also { p ->
                    p.initialize(context)
                    p.projectFile = folder
                }
            }
        }

        fun create(location: File, context: Context) = XenakisProject(
            settings = InteractionSettings.default(),
            groups = GroupRegistry.createDefault().also { r -> r.initialize(context) },
            busses = BusRegistry.createDefault().also { r -> r.initialize(context) },
            buffers = BufferRegistry(mutableListOf()).also { r -> r.initialize(context) },
            instruments = InstrumentRegistry.newInstance().also { r -> r.initialize(context) },
            flowGraph = AudioFlowGraph.createDefault().also { g -> g.initialize(context) },
            globalControls = GlobalControls(mutableListOf()).also { c -> c.initialize(context) },
            serverSetup = EditorRoot.create(CodeBlockEditor(context)),
            serverTree = EditorRoot.create(CodeBlockEditor(context)),
            score = Score().also { score -> score.initialize(context, reactiveValue("<root>")) },
        ).also { project ->
            project.initialize(context)
            project.projectFile = location
        }
    }
}