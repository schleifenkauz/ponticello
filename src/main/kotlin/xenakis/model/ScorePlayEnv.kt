package xenakis.model

import reaktive.value.now
import xenakis.impl.ScWriter
import xenakis.ui.ScorePlayer.LocatedScoreObject
import java.util.*

class ScorePlayEnv(val writer: ScWriter) {
    private val activeObjects: PriorityQueue<LocatedScoreObject> =
        PriorityQueue(compareBy { (obj, _, pos) -> pos.time + obj.duration })

    fun markObjectStart(obj: LocatedScoreObject) {
        activeObjects.offer(obj)
    }

    fun advanceToTime(t: Double) {
        while (true) {
            if (activeObjects.isEmpty()) break
            val (obj, _, pos) = activeObjects.peek()
            if (t > pos.time + obj.duration) activeObjects.remove()
            else break
        }
    }

    fun activeSynths(group: GroupObjectReference) =
        activeObjects.filter { (obj, _, _) -> obj is SynthObject && obj.group.now == group }

    fun nearestSynthFromTop(y: Double) = activeObjects.filter { (obj, _, pos) ->
        obj is SynthObject && pos.y > y
    }.minByOrNull { (_, _, pos) -> pos.y }
}