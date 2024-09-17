package xenakis.model

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import reaktive.value.reactiveValue
import reaktive.value.reactiveVariable
import xenakis.model.ScoreEventCollector.Event
import kotlin.random.Random

class ScoreEventCollectorTest {
    @Test
    fun randomlyAddAndRemoveObjects() {
        val context = Utils.createContext()
        val rootScore = Score()
        rootScore.initialize(context, reactiveValue("<root>"))
        val collector = ScoreEventCollector(rootScore, null, null)
        val objects = mutableListOf<ScoreObject>()
        val subScores = mutableListOf<Score>()
        val rnd = Random(1000)
        for (i in 0..1000) {
            val p = rnd.nextDouble()
            when {
                rootScore.objectInstances.isEmpty() || p < 0.5 -> {
                    val obj =
                        if (objects.isEmpty() || rnd.nextBoolean()) {
                            Utils.createDummyObject("obj${objects.size}").also { objects.add(it) }
                        } else objects.random()
                    val parentScore =
                        if (subScores.isEmpty() || rnd.nextDouble() < 0.8) rootScore else subScores.random()
                    val time = rnd.nextDouble(100.0)
                    val y = rnd.nextDouble(100.0)
                    val muted = rnd.nextDouble() <= 0.2
                    parentScore.addObject(ScoreObjectInstance(obj.createReference(), time, y, muted))
                }

                p < 0.65 -> {
                    val inst = rootScore.allInstances().toList().random()
                    inst.score.removeObject(inst)
                }

                p < 0.8 -> {
                    val inst = rootScore.allInstances().toList().random()
                    inst.toggleMuted()
                }

                p < 0.9 -> {
                    val time = rnd.nextDouble(100.0)
                    val y = rnd.nextDouble(100.0)
                    val inst = rootScore.allInstances().toList().random()
                    inst.moveTo(time, y)
                }

                else -> {
                    val parentScore =
                        if (subScores.isEmpty() || rnd.nextDouble() < 0.8) rootScore else subScores.random()
                    val subScore = Score()
                    subScores.add(subScore)
                    val obj = ScoreObjectGroup(reactiveVariable("score${subScores.size}"), subScore)
                    obj.setInitialSize(100.0, 100.0)
                    val time = rnd.nextDouble(100.0)
                    val y = rnd.nextDouble(100.0)
                    parentScore.addObject(ScoreObjectInstance(obj.createReference(), time, y))
                }
            }
            checkEvents(rootScore, collector)
        }
    }

    private fun expectedEvents(score: Score, scorePosition: ObjectPosition = ObjectPosition(0.0, 0.0)): List<Event> =
        score.objectInstances.flatMap { inst ->
            val obj = inst.obj
            if (inst.muted) emptyList()
            else if (obj is ScoreObjectGroup) expectedEvents(obj.score, scorePosition + inst.position)
            else {
                val startPos = inst.position + scorePosition
                listOf(
                    Event(Event.Type.ObjectStart, startPos, inst),
                    Event(Event.Type.ObjectEnd, startPos + ObjectPosition(inst.obj.duration, 0.0), inst)
                )
            }
        }

    private fun checkEvents(score: Score, collector: ScoreEventCollector) {
        val expectedEvents = expectedEvents(score).sorted()
        val actualEvents = collector.eventsAt(0.0, delta = 10000.0)
        assertArrayEquals(expectedEvents.toTypedArray(), actualEvents.toTypedArray())
        println("CHECK OKAY: ${expectedEvents.size} events")
    }

    @Test
    fun addInstanceToMutedSubScore() {
        val context = Utils.createContext()
        val rootScore = Score()
        rootScore.initialize(context, reactiveValue("<root>"))
        val collector = ScoreEventCollector(rootScore, null, null)
        val subScore = Score()
        val subObj = ScoreObjectGroup(reactiveVariable("sub_score"), subScore)
        val subInst = ScoreObjectInstance(subObj.createReference(), 10.0, 100.0)
        rootScore.addObject(subInst)
        val obj = Utils.createDummyObject("obj1")
        subInst.toggleMuted()
        subScore.addObject(ScoreObjectInstance(obj.createReference(), 10.0, 10.0))
        assertArrayEquals(collector.eventsAt(0.0, 10000.0).toTypedArray(), emptyArray())
        subInst.toggleMuted()
        checkEvents(rootScore, collector)
    }
}