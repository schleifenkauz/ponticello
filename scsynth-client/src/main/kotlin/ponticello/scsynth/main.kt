package ponticello.scsynth

import java.io.DataInputStream
import java.io.File

fun main(args: Array<String>) {
    val filename = args[0]
    val file = File(filename)
    val synthDefs = DataInputStream(file.inputStream()).use { input ->
        SynthDefParser(input).parse()
    }
    println(synthDefs)
    /*DataOutputStream(file.outputStream()).use { output ->
        SynthDefWriter(output).write(synthDefs)
    }*/
}