package ponticello.model.sc

import ponticello.sc.client.ScWriter

class SoundProcessProxy(private val writer: ScWriter, private val enclosingFunc: ScoreObjectFunc) {
    fun registerAuxilBus(parameter: String, expr: String) {
        writer.appendLine("${enclosingFunc.getObject()}.registerBus($parameter, $expr)")
    }

    fun registerAuxilSynth(parameter: String, expr: String) {
        writer.appendLine("${enclosingFunc.getObject()}.registerAuxilSynth($parameter, $expr)")
    }
}