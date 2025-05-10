package ponticello.model.player

import bundles.PublicProperty
import bundles.publicProperty
import reaktive.value.now
import ponticello.model.score.Envelope
import ponticello.model.score.controls.UGenControl
import ponticello.sc.Warp
import ponticello.sc.client.ScWriter
import ponticello.sc.code

class AuxilSynthDefManager {
    private val envSynthDefSuffixes = mutableMapOf<Envelope, Int>()
    private val envSynthCode = mutableMapOf<Envelope, String>()
    private var nextEnvSynthDefSuffix = 0

    private val auxilSynthDefSuffixes = mutableMapOf<UGenControl, Int>()
    private val auxilSynthCode = mutableMapOf<UGenControl, String>()
    private var nextAuxilSynthDefSuffix = 0

    fun ScWriter.defineAuxilSynthDef(control: UGenControl): String {
        val suffix = auxilSynthDefSuffixes.getOrPut(control) { nextAuxilSynthDefSuffix++ }
        val name = "\\auxil_$suffix"
        val code = control.expr.editor.result.now.code(control.context)
        if (auxilSynthCode[control] != code)
        +"SynthDef($name) { $code }.add"
        return name
    }

    fun ScWriter.defineEnvelopeSynthDef(envelope: Envelope, warp: Warp): String {
        val suffix = envSynthDefSuffixes.getOrPut(envelope) { nextEnvSynthDefSuffix++ }
        val name = "\\env_$suffix"
        val code = envelope.code(warp)
        if (envSynthCode[envelope] != code) {
            +"SynthDef($name) { |out| Out.kr(out, $code) }.add"
            +"s.sync"
        }
        return name
    }

    companion object : PublicProperty<AuxilSynthDefManager> by publicProperty("AuxilSynthDefManager")
}