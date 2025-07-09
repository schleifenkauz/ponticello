package ponticello.scsynth

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

fun main(args: Array<String>) {
    val filename = args[0]
    val synthDefs = readSynthDefs(filename)
    println(synthDefs)
    val file = File(filename)
    DataOutputStream(file.outputStream()).use { output ->
        SynthDefWriter(output).write(synthDefs)
    }
}

fun readSynthDefs(filename: String): List<CompiledSynthDef> {
    val file = File(filename)
    val synthDefs = DataInputStream(file.inputStream()).use { input ->
        SynthDefParser(input).parse()
    }
    return synthDefs
}