package xenakis.model.flow

object BubbleSort {
    fun <V> sort(list: MutableList<V>, comparator: Comparator<V>, moveAfter: (V, V) -> Unit = { _, _ -> }) {
        var n = list.size
        while (n > 1) {
            var j = 0
            var newN = 0
            for (i in 1 until n) {
                if (comparator.compare(list[i - 1], list[i]) > 0) {
                    val v = list[i - 1]
                    list[i - 1] = list[i]
                    list[i] = v
                    newN = i
                } else {
                    if (j != i - 1) {
                        moveAfter(list[i - 1], list[i])
                    }
                    j = i
                }
            }
            if (j != n - 1) {
                moveAfter(list[n - 2], list[n - 1])
            }
            n = newN
        }
    }
}