package ponticello.impl

object BubbleSort {
    fun <V> sort(list: MutableList<V>, comparator: Comparator<V>, moveAfter: (V, V) -> Unit = { _, _ -> }) {
        for (v in list.toList()) {
            val i = list.indexOf(v)
            val j = list.drop(i + 1).indexOfLast { u ->
                val x = comparator.compare(v, u)
                x > 0
            }
            if (j == -1) continue
            val u = list[j + i + 1]
            moveAfter(u, v)
            list.add(j + i + 2, v)
            list.removeAt(i)
        }
        return
//        val n = list.size
//        repeat(list.size) {
//            var j = 0
//            for (i in 1 until n) {
//                if (comparator.compare(list[i - 1], list[i]) > 0) {
//                    val v = list[i - 1]
//                    list[i - 1] = list[i]
//                    list[i] = v
//                } else {
//                    if (j != i - 1) {
//                        moveAfter(list[i - 1], list[i])
//                    }
//                    j = i
//                }
//            }
//            if (j != n - 1) {
//                moveAfter(list[n - 2], list[n - 1])
//            }
//        }
    }
}