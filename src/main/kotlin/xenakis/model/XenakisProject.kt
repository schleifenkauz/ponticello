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
import reaktive.Observer
import reaktive.value.now
import xenakis.impl.SuperColliderClient
import xenakis.impl.SuperColliderContext
import xenakis.impl.SuperColliderWriterContext
import xenakis.impl.UDPSuperColliderClient
import xenakis.sc.code
import xenakis.sc.editor.CodeBlockEditor
import java.io.File
import java.io.Writer

@Serializable
class XenakisProject private constructor(
    val serverSetup: EditorRoot<CodeBlockEditor>,
    val beforePlay: EditorRoot<CodeBlockEditor>,
    val synthDefs: SynthDefRegistry,
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
    private lateinit var statusObserver: Observer

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
        synthDefs.initialize(context)
        score.initialize(context)
        context[SynthDefRegistry] = synthDefs
        client = context[SuperColliderClient]
        statusObserver = client.statusListener.statusUpdates.observe { _, status ->
            if (status == UDPSuperColliderClient.StatusUpdate.ReadyToBoot) {
                bootServer(client)
            }
        }
    }

    fun saveTo(file: File) {
        val str = Json.encodeToString(this)
        file.writeText(str)
    }

    fun exportAsScript(writer: Writer) {
        val context = SuperColliderWriterContext(writer)
        bootServer(context)
        prepareForPlay(context)
        context.run { score.writePlayerTask(this, startTime = 0.0, taskName = "play_score", SuffixGenerator.None) }
    }

    fun playScore(fromTime: Double) {
        val suffixGenerator = context[SuffixGenerator]
        SuperColliderWriterContext.wrap(client) {
            prepareForPlay(this)
            run { score.writePlayerTask(this, fromTime, taskName = "play_score", suffixGenerator) }
        }
    }

    fun rebootServer() {
        client.run("s.quit")
        bootServer(client)
    }

    fun bootServer(context: SuperColliderContext) {
        SuperColliderWriterContext.wrap(context) {
            run {
                appendBlock("Task") {
                    +"s.bootSync"
                    busses.run { reallocateBusses() }
                    buffers.run { initializeBuffers() }
                    globalControls.run { setBusValues() }
                    groups.run { setupGroups() }
                    for (obj in score.objects) obj.run { serverBooted(this@wrap) }
                    run(serverSetup.editor.result.now.code)
                    run("\"Server is setup\".postln;")
                }
                appendLine(".play;")
            }
        }
    }

    fun prepareForPlay(context: SuperColliderContext) {
        SuperColliderWriterContext.wrap(context) {
            run(beforePlay.editor.result.now.code)
            flowGraph.run { setupAudioFlow() }
            synthDefs.run { addSynthDefs() }
            groups.run { setupGroups() }
        }
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
            synthDefs = SynthDefRegistry.newInstance(),
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