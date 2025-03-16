package xenakis.model

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import reaktive.value.now
import reaktive.value.reactiveVariable
import xenakis.impl.asTime
import xenakis.impl.asY
import xenakis.impl.withPrecision
import xenakis.impl.zero
import xenakis.model.player.ScoreEventCollector
import xenakis.model.player.ScoreEventCollector.Event
import xenakis.model.registry.reference
import xenakis.model.score.*
import kotlin.random.Random

class ScoreEventCollectorTest {
    @Test
    fun randomlyAddAndRemoveObjects() {
        val context = Utils.createContext()
        val rootScore = Score()
        rootScore.initialize(context, null)
        val collector = ScoreEventCollector(rootScore, null, context[Settings])
        val objects = mutableListOf<ScoreObject>()
        val subScores = mutableListOf<Score>()
        val rnd = Random(100)
        for (i in 0..1000) {
            val p = rnd.nextDouble()
            when {
                rootScore.objectInstances.isEmpty() || p < 0.5 -> {
                    val obj =
                        if (objects.isEmpty() || rnd.nextBoolean()) {
                            Utils.createDummyObject("obj${objects.size}").also { objects.add(it) }
                        } else objects.random(rnd)
                    val parentScore =
                        if (subScores.isEmpty() || rnd.nextDouble() < 0.8) rootScore else subScores.random(rnd)
                    val time = rnd.nextDouble(100.0).asTime
                    val y = rnd.nextDouble(100.0).asY
                    val muted = rnd.nextDouble() <= 0.2
                    val inst = ScoreObjectInstance(obj, time, y, reactiveVariable(muted))
                    parentScore.addObject(inst)
                    println("Add $inst")
                }

                p < 0.65 -> {
                    val inst = rootScore.allInstances().toList().random(rnd)
                    println("Remove $inst")
                    inst.score?.removeObject(inst)
                }

                p < 0.8 -> {
                    val inst = rootScore.allInstances().toList().random(rnd)
                    inst.toggleMuted()
                    println("Toggle mute $inst = ${inst.muted}")
                }

                p < 0.9 -> {
                    val time = rnd.nextDouble(100.0).asTime
                    val y = rnd.nextDouble(100.0).asY
                    val inst = rootScore.allInstances().toList().random(rnd)
                    inst.moveTo(time, y, simpleMove = true)
                    println("Moved $inst")
                }

                else -> {
                    val parentScore =
                        if (subScores.isEmpty() || rnd.nextDouble() < 0.8) rootScore else subScores.random(rnd)
                    val subScore = Score()
                    subScores.add(subScore)
                    val obj = ScoreObjectGroup(reactiveVariable("score${subScores.size}"), subScore)
                    obj.setInitialSize(100.0.asTime, 100.0.withPrecision(ObjectPosition.Y_PRECISION))
                    val time = rnd.nextDouble(100.0).asTime
                    val y = rnd.nextDouble(100.0).asY
                    val inst = ScoreObjectInstance(obj, time, y)
                    parentScore.addObject(inst)
                    println("Added $inst")
                }
            }
            checkEvents(rootScore, collector)
        }
    }

    private fun expectedEvents(score: Score, scorePosition: ObjectPosition = ObjectPosition(0.0, 0.0)): List<Event> =
        score.objectInstances.flatMap { inst ->
            val obj = inst.obj
            if (inst.muted.now) emptyList()
            else if (obj is ScoreObjectGroup) expectedEvents(obj.score, scorePosition + inst.position)
            else {
                val startPos = inst.position + scorePosition
                listOf(
                    Event(Event.Type.ObjectStart, startPos, inst),
                    Event(Event.Type.ObjectEnd, startPos + ObjectPosition(inst.obj.duration, zero), inst)
                )
            }
        }

    private fun checkEvents(score: Score, collector: ScoreEventCollector) {
        val expectedEvents = expectedEvents(score).sortedBy { ev -> ev.absolutePosition }
        val actualEvents = collector.eventsAt(zero, delta = 10000.0.asTime)
        assertArrayEquals(expectedEvents.toTypedArray(), actualEvents.toTypedArray())
        println("CHECK OKAY: ${expectedEvents.size} events")
    }

    @Test
    fun addInstanceToMutedSubScore() {
        val context = Utils.createContext()
        val rootScore = Score()
        rootScore.initialize(context, null)
        val collector = ScoreEventCollector(rootScore, null, context[Settings])
        val subScore = Score()
        val subObj = ScoreObjectGroup(reactiveVariable("sub_score"), subScore)
        val subInst =
            ScoreObjectInstance(subObj.reference(), 10.0.asTime, 100.0.withPrecision(ObjectPosition.Y_PRECISION))
        rootScore.addObject(subInst)
        val obj = Utils.createDummyObject("obj1")
        subInst.toggleMuted()
        subScore.addObject(
            ScoreObjectInstance(
                obj.reference(),
                10.0.asTime,
                10.0.asY
            )
        )
        checkEvents(rootScore, collector)
        subInst.toggleMuted()
        checkEvents(rootScore, collector)
    }
}