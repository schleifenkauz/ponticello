package xenakis.model

import bundles.set
import hextant.context.Context
import hextant.context.withoutUndo
import hextant.serial.EditorRoot
import hextant.serial.SnapshotAware
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import reaktive.Observer
import reaktive.list.observeEach
import reaktive.value.now
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
    val synthDefs: SynthDefs,
    val flowGraph: AudioFlowGraph,
    val buffers: Buffers,
    val score: Score
) {
    @Transient
    lateinit var context: Context
        private set

    @Transient
    lateinit var client: UDPSuperColliderClient
        private set

    @Transient
    private lateinit var colorObserver: Observer

    @Transient
    lateinit var projectFile: File
        private set

    fun initialize(context: Context) {
        this.context = context
        for (obj in score.objects) obj.context = context
        context[SynthDefs] = synthDefs
        client = context[UDPSuperColliderClient]
        colorObserver = synthDefs.editor.editor.editors.observeEach { _, def ->
            def.associatedColor.result.observe { _ ->
                score.recoloredSynthDef(def.name.text.now)
            }
        }
        if (client.status == UDPSuperColliderClient.Status.Listening) {
            setupServer(client)
            for (obj in score.objects) obj.initialize(this)
        }
        client.addStatusListener { status ->
            if (status == UDPSuperColliderClient.Status.Listening) {
                setupServer(client)
            }
        }
    }

    fun saveTo(file: File) {
        val str = Json.encodeToString(this)
        file.writeText(str)
    }

    fun exportAsScript(writer: Writer) {
        val context = SuperColliderWriterContext(writer)
        setupServer(context)
        prepareForPlay(context)
        context.postAsync { score.writePlayerTask(this, startTime = 0.0) }
    }

    fun playScore(fromTime: Double) = SuperColliderWriterContext.wrap(client) {
        prepareForPlay(this)
        postAsync { score.writePlayerTask(this, fromTime) }
    }

    fun setupServer(context: SuperColliderContext) {
        SuperColliderWriterContext.wrap(context) {
            postAsync {
                appendBlock("Task") {
                    +"s.sync"
                    flowGraph.allocateBusses(this@wrap)
                    synthDefs.reload(this@wrap)
                    buffers.loadBuffers(this@wrap)
                    postAsync(serverSetup.editor.result.now.code)
                }
                appendLine(".play;")
            }
        }
    }

    fun prepareForPlay(context: SuperColliderContext) {
        SuperColliderWriterContext.wrap(context) {
            postAsync(beforePlay.editor.result.now.code + ";")
            flowGraph.setupAudioFlow(this)
            synthDefs.reload(this)
        }
    }

    fun renamedSynthDef(oldName: String, newName: String) {
        for (obj in score.objects) {
            if (obj is SynthObject && obj.synthDefName == oldName) {
                obj.synthDefName = newName
            }
        }
        synthDefs.renamedSynthDef(oldName, newName)
    }

    companion object {
        fun loadFrom(file: File, context: Context): XenakisProject {
            val str = file.readText()
            SnapshotAware.Serializer.reconstructionContext = context
            val project = context.withoutUndo { Json.decodeFromString<XenakisProject>(str) }
            project.projectFile = file
            project.initialize(context)
            return project
        }

        fun create(location: File, context: Context) = XenakisProject(
            serverSetup = EditorRoot.create(CodeBlockEditor(context)),
            beforePlay = EditorRoot.create(CodeBlockEditor(context)),
            synthDefs = SynthDefs.newInstance(context),
            flowGraph = AudioFlowGraph.createDefault(),
            buffers = Buffers(mutableListOf()),
            score = Score(),
        ).also { project ->
            project.initialize(context)
            project.projectFile = location
        }
    }
}