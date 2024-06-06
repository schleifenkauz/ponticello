package xenakis.model

import bundles.set
import hextant.context.Context
import hextant.context.withoutUndo
import hextant.serial.EditorRoot
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import reaktive.value.now
import xenakis.impl.ScWriter
import xenakis.impl.StatusListener.StatusUpdate
import xenakis.impl.SuperColliderClient
import xenakis.sc.code
import xenakis.sc.editor.CodeBlockEditor
import java.io.File

@Serializable
class XenakisProject private constructor(
    val serverSetup: EditorRoot<CodeBlockEditor>,
    val beforePlay: EditorRoot<CodeBlockEditor>,
    val instruments: InstrumentRegistry,
    val busses: BusRegistry,
    val flowGraph: AudioFlowGraph,
    val buffers: BufferRegistry,
    val globalControls: GlobalControls = GlobalControls(mutableListOf()),
    val groups: GroupRegistry = GroupRegistry(),
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
        groups.initialize(context)
        buffers.initialize(context)
        busses.initialize(context)
        flowGraph.initialize(context)
        globalControls.initialize(context)
        instruments.initialize(context)
        score.initialize(context)
        context[InstrumentRegistry] = instruments
        client = context[SuperColliderClient]
        client.statusListener.on(StatusUpdate.ReadyToBoot) {
            client.run {
                addServerBootHooks()
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

    fun saveTo(file: File) {
        val str = Json.encodeToString(this)
        file.writeText(str)
    }

    fun exportAsScript(output: Appendable) {
        with(ScWriter(output)) {
            serverSetup.editor.result.now.code(writer)
            beforePlay.editor.result.now.code(writer)
            groups.run { allocateAll() }
            groups.run { allocateAll() }
            flowGraph.run { setupAudioFlow() }
            globalControls.run { setBusValues() }
            instruments.run { allocateAll() }
            score.writePlayerTask(writer, startTime = 0.0, taskName = "play_score", prefix = "")
        }
    }

    fun ScWriter.playScore(fromTime: Double) {
        beforePlay.editor.result.now.code(this)
        score.writePlayerTask(this, fromTime, taskName = "play_score", prefix = "")
    }

    private fun bootServer() {
        client.run("s.boot;")
    }

    fun rebootServer() {
        client.run("s.reboot;")
    }

    companion object {
        fun loadFrom(file: File, context: Context): XenakisProject {
            val str = file.readText()
            val project = context.withoutUndo { Json.decodeFromString<XenakisProject>(str) }
            project.projectFile = file
            project.initialize(context)
            return project
        }

        fun create(location: File, context: Context) = XenakisProject(
            serverSetup = EditorRoot.create(CodeBlockEditor(context)),
            beforePlay = EditorRoot.create(CodeBlockEditor(context)),
            instruments = InstrumentRegistry.newInstance(),
            busses = BusRegistry.createDefault(),
            flowGraph = AudioFlowGraph.createDefault(),
            buffers = BufferRegistry(mutableListOf()),
            score = Score(),
        ).also { project ->
            project.initialize(context)
            project.projectFile = location
        }
    }
}