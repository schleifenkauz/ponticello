package xenakis.model.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import xenakis.impl.ReachabilityGraph
import kotlin.random.Random

class ReachabilityGraphTest {
    private val r = Random(1000)

    @Test
    fun reachabilityTestAutomated() {
        val g = ReachabilityGraph<Int>()
        for (i in 0 until N) g.addVertex(i)
        repeat(10000) { iter ->
            if (g.edges().size >= 3 && r.nextBoolean()) {
                val (v, u) = g.edges().random(r)
                //println("remove edge $v -> $u")
                g.removeEdge(v, u)
            } else {
                val v = r.nextInt(N)
                val u = r.nextInt(N)
                //println("add edge $v -> $u")
                try {
                    g.addEdge(v, u)
                } catch (e: ReachabilityGraph.LoopException) {
                    System.err.println("LOOP")
                }
            }
            for (v in 0 until N) {
                for (u in 0 until N) {
                    assertEquals(
                        g.dfsReachable(v, u),
                        g.reachable(v, u),
                        "Reachability mismatch on iteration $iter: $v -> $u\n graph: $g"
                    )
                }
            }
        }
    }

    @Test
    @Disabled
    fun customTest() {
        val g = ReachabilityGraph<Int>()
        for (v in 0 until 6) g.addVertex(v)
        g.addEdge(0, 5)
        g.addEdge(4, 5)
        g.addEdge(5, 0)
        g.addEdge(3, 4)
        assertEquals(true, g.reachable(3, 0))
        g.removeEdge(3, 4)
        assertEquals(false, g.reachable(3, 0))


    }

    companion object {
        private const val N = 50
    }
}