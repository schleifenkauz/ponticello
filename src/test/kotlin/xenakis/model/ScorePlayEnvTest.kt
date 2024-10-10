package xenakis.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import xenakis.impl.asTime
import xenakis.impl.toDecimal
import xenakis.model.ScorePlayEnv.SynthOrder

class ScorePlayEnvTest {
    @Test
    fun testGeneratingSuffixes() {
        val env = ScorePlayEnv(settings)
        val dummy1 = Utils.createDummyObject("synth")
        val inst1 = ScoreObjectInstance(dummy1.createReference(), 5.0.asTime, 100.0.toDecimal())
        val name1 = env.markStart(inst1, ObjectPosition(5.0, 100.0))
        assertEquals("synth", name1)
        val name2 = env.markStart(inst1, ObjectPosition(10.0, 300.0))
        assertEquals("synth_1", name2)
        env.markEnd(inst1, ObjectPosition(5.0, 100.0))
        val name3 = env.markStart(inst1, ObjectPosition(15.0, 500.0))
        assertEquals("synth", name3)
    }

    @Test
    fun testSynthOrder() {
        val env = ScorePlayEnv(settings)
        val dummy1 = Utils.createDummyObject("synth")
        val inst1 = ScoreObjectInstance(dummy1.createReference(), 3.0.asTime, 100.0.toDecimal())
        val group = Utils.defaultGroup
        val name1 = env.markStart(inst1, ObjectPosition(3.0, 100.0))
        val order1 = env.getSynthOrderFor(group, ObjectPosition(5.0, 200.0))
        assertEquals(SynthOrder("'addAfter'", "~synths['${name1}']"), order1)
        val order2 = env.getSynthOrderFor(group, ObjectPosition(5.0, 0.0))
        assertEquals(SynthOrder("'addBefore'", "~synths['${name1}']"), order2)
        val order3 = env.getSynthOrderFor(group, ObjectPosition(3.0, 200.0))
        assertEquals(SynthOrder("'addToHead'", "s.defaultGroup"), order3)
    }

    companion object {
        private val settings = Settings.createDefault()
    }
}