package xenakis.model.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.random.Random

class BubbleSortTest {
    @Test
    fun automatedSortTest() {
        val r = Random(1000)
        for (n in 2..500) {
            val list = MutableList(n) { r.nextInt(n) }
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