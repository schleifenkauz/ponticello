package xenakis.sc

import java.util.*

inline fun <reified R> ScElement.allChildren(): List<R> = buildList {
    val queue: Queue<ScElement> = LinkedList()
    queue.offer(this@allChildren)
    while (queue.isNotEmpty()) {
        val element = queue.poll()
        if (element is R) add(element)
        for (child in element.children) {
            queue.offer(child)
        }
    }
}

fun ScElement.visit(visitor: (ScElement) -> Unit) {
    visitor(this)
    for (element in children) visitor(element)
}