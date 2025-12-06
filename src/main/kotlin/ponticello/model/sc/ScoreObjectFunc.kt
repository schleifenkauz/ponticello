package ponticello.model.sc

import ponticello.model.score.ScoreObject

class ScoreObjectFunc(private val obj: ScoreObject) {
    val cutoff: String get() = "cutoff"
    val serverLatency: String get() = "server_latency"
    val target: String get() = "placement.target"
    val addAction: String get() = "placement.addAction"
    private val uniqueName: String get() = "idx"

    val synthVar = "synth"

    val processVar = "proc"

    fun getObject(): String = "${obj.superColliderName}.getInstance()"
}