package ponticello.scapi

import ponticello.scsynth.SynthDefWriter
import ponticello.scsynth.readSynthDefs
import java.io.DataOutputStream
import java.io.File

fun main() {
    val compiler = SynthDefCompiler()
    val name = "quinte5"
    val def = SynthDef(name) {
        var snd = (SinOsc.ar(c(440, 660))).sum
        val amp = SinOsc.kr("amp_rate".kr(0.5f)).linexp((-1).c, 1.c, 0.01.c, 0.3.c)
        val env = env(c(0, 1, 1, 0), c(10.0, 10.0, 5.0), "lin")
        snd = snd * amp
        snd = snd * env.kr(Done.FREE_SELF, levelScale = 0.1.c)
        val pan = SinOsc.kr("pan_rate".kr(0.1f))
        snd = Pan2.ar(snd, pan)
        Out.ar(0, snd)
    }
    println(def.ugenGraph())

    println("#############################################################")

    val compiled = compiler.compileSynthDef(def)
    println(compiled)

    val file = File("C:\\Users\\nikok\\AppData\\Local\\SuperCollider\\synthdefs\\$name.scsyndef")
    DataOutputStream(file.outputStream()).use { output ->
        val writer = SynthDefWriter(output)
        writer.write(listOf(compiled))
    }

    println("#############################################################")

    readSynthDefs(file.absolutePath).forEach { println(it) }
}