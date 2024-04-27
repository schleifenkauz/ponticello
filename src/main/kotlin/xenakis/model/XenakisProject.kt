package xenakis.model

import hextant.context.Context
import hextant.context.withoutUndo
import hextant.serial.SnapshotAware
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import xenakis.sc.Buffer
import xenakis.sc.ScExpr
import xenakis.sc.SynthDef
import java.io.File
import java.io.Writer

@Serializable
class XenakisProject(
    val globalVariables: MutableMap<String, ScExpr>, val synthDefs: MutableList<SynthDef>,
    val flowGraph: AudioFlowGraph, val buffers: MutableList<Buffer>,
    val score: Score
) {
    @Transient
    lateinit var context: Context

    init {
        for (obj in score.objects) {
            obj.initialize(this)
        }
    }

    fun getSynthDef(name: String): SynthDef =
        synthDefs.find { it.name == name } ?: error("no SynthDef with name '$name'")

    fun saveTo(file: File) {
        val str = Json.encodeToString(this)
        file.writeText(str)
    }

    fun exportAsScript(writer: Writer) {
        TODO("Not yet implemented")
    }

    companion object {
        fun loadFrom(file: File, context: Context): XenakisProject {
            val str = file.readText()
            SnapshotAware.Serializer.reconstructionContext = context
            val project = context.withoutUndo { Json.decodeFromString<XenakisProject>(str) }
            project.context = context
            return project
        }
    }
}