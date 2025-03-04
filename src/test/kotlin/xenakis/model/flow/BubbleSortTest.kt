package xenakis.model.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import xenakis.impl.BubbleSort
import kotlin.random.Random

class BubbleSortTest {
    @Test
    fun automatedSortTest() {
        val r = Random(1000)
        for (n in 2..500) {
            val list = (0..n * 3).shuffled(r).toMutableList()
            val sorted = list.sorted()
            BubbleSort.sort(list, Comparator.naturalOrder())
            assertEquals(sorted, list)
        }
    }

    @Test
    fun customTest1() {
        val list = mutableListOf(3, 2, 4, 1)
        val sorted = list.sorted()
        BubbleSort.sort(list, Comparator.naturalOrder()) { x, y -> println("move $y after $x") }
        assertEquals(sorted, list)
    }
}