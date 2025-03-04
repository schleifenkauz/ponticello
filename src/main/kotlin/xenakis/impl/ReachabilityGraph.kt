package xenakis.impl

import java.util.*

class ReachabilityGraph<V> {
    private val edges = mutableSetOf<Pair<V, V>>()
    private val edgesFrom = mutableMapOf<V, MutableSet<V>>()
    private val backEdges = mutableMapOf<V, MutableSet<V>>()
    private val reaches = mutableMapOf<V, MutableSet<V>>()

    fun edges(): Set<Pair<V, V>> = edges

    fun reachable(from: V, target: V): Boolean {
        return from in reaches.getValue(target)
    }

    fun dfsReachable(from: V, target: V): Boolean {
        val q: Queue<V> = LinkedList()
        val visited = mutableSetOf(from)
        q.offer(from)
        while (q.isNotEmpty()) {
            val v = q.poll()
            if (v == target) return true
            for (u in edgesFrom(v)) {
                if (visited.add(u)) q.offer(u)
            }
        }
        return false
    }

    fun addVertex(vertex: V) {
        edgesFrom[vertex] = mutableSetOf()
        backEdges[vertex] = mutableSetOf()
        reaches[vertex] = mutableSetOf(vertex)
    }

    fun addEdge(from: V, target: V) {
        if (from == target) return
        if (reachable(target, from)) throw LoopException()
        if (edgesFrom(from).add(target)) {
            backEdges.getValue(target).add(from)
            edges.add(from to target)
            propagate(target, reaches.getValue(from))
        }
    }

    private fun propagate(v: V, set: Set<V>) {
        val added = set - reaches.getValue(v)
        if (added.isNotEmpty()) {
            reaches.getValue(v).addAll(added)
            for (u in edgesFrom(v)) {
                propagate(u, added)
            }
        }
    }

    fun removeEdge(from: V, target: V) {
        if (from == target) return
        if (edgesFrom(from).remove(target)) {
            backEdges.getValue(target).remove(from)
            edges.remove(from to target)
            propagateRemove(target, reaches.getValue(from))
        }
    }

    private fun propagateRemove(v: V, set: Set<V>) {
        val s = set.toMutableSet()
        for (u in backEdges.getValue(v)) {
            s.removeIf(reaches.getValue(u)::contains) //TODO doesn't work if there are loops
        }
        s.removeIf { w -> w !in reaches.getValue(v) }
        s.remove(v)
        if (s.isNotEmpty()) {
            reaches.getValue(v).removeAll(s)
            for (u in edgesFrom(v)) {
                propagateRemove(u, s)
            }
        }
    }

    fun removeVertex(v: V) {
        for (u in edgesFrom(v)) {
            removeEdge(v, u)
        }
        edgesFrom.remove(v)
        reaches.remove(v)
    }

    private fun edgesFrom(v: V) = edgesFrom.getValue(v)

    override fun toString(): String = buildString {
        for ((v, edges) in edgesFrom) {
            appendLine("$v -> $edges")
        }
    }

    class LoopException: Exception("There is a loop in the graph!")
}